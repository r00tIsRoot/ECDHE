package com.example.ecdhe.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ecdhe.ecdhe.*
import com.example.ecdhe.ui.components.ECCurveCanvas
import com.example.ecdhe.ui.components.GraphPoint
import com.example.ecdhe.ui.components.PointType

// ---- 상태 정의 ----

/** ECDHE 시각화의 단계 */
enum class ECDHEStep(val label: String, val description: String) {
    PARAMS("파라미터", "타원곡선과 생성점 G를 설정합니다"),
    ALICE_KEYGEN("앨리스 키생성", "앨리스가 개인키 dA와 공개키 QA를 생성합니다"),
    BOB_KEYGEN("밥 키생성", "밥이 개인키 dB와 공개키 QB를 생성합니다"),
    EXCHANGE("공개키 교환", "앨리스와 밥이 공개키를 교환합니다"),
    SHARED_SECRET("공유 비밀", "양쪽이 동일한 세션키 S를 계산합니다")
}

/** 화면 상태 홀더 */
class ECDHEScreenState {
    var currentStep by mutableStateOf(ECDHEStep.PARAMS)
    var alicePrivateKey by mutableStateOf(3)
    var bobPrivateKey by mutableStateOf(2)
    var curve by mutableStateOf(EllipticCurve(a = -1.0, b = 1.0))
    var generator by mutableStateOf(ECPoint(0.0, 1.0))
    var engine by mutableStateOf(ECDHEEngine())
    var result by mutableStateOf<ECDHEResult?>(null)
    var aliceInput by mutableStateOf("3")
    var bobInput by mutableStateOf("2")
    var showError by mutableStateOf(false)

    /** 현재 단계의 그래프 점들 반환 */
    fun getGraphPoints(): List<GraphPoint> {
        val points = mutableListOf<GraphPoint>()

        // 항상 generator 표시
        points.add(GraphPoint(generator, "G", PointType.GENERATOR))

        when (currentStep) {
            ECDHEStep.PARAMS -> {
                // Generator만 표시
            }
            ECDHEStep.ALICE_KEYGEN -> {
                val qa = engine.generateKeyPair(curve, generator, alicePrivateKey).publicKey
                if (!qa.isInfinity) {
                    points.add(GraphPoint(qa, "QA", PointType.ALICE_PUBLIC))
                }
            }
            ECDHEStep.BOB_KEYGEN -> {
                val qa = engine.generateKeyPair(curve, generator, alicePrivateKey).publicKey
                val qb = engine.generateKeyPair(curve, generator, bobPrivateKey).publicKey
                if (!qa.isInfinity) points.add(GraphPoint(qa, "QA", PointType.ALICE_PUBLIC))
                if (!qb.isInfinity) points.add(GraphPoint(qb, "QB", PointType.BOB_PUBLIC))
            }
            ECDHEStep.EXCHANGE -> {
                val qa = engine.generateKeyPair(curve, generator, alicePrivateKey).publicKey
                val qb = engine.generateKeyPair(curve, generator, bobPrivateKey).publicKey
                if (!qa.isInfinity) points.add(GraphPoint(qa, "QA", PointType.ALICE_PUBLIC))
                if (!qb.isInfinity) points.add(GraphPoint(qb, "QB", PointType.BOB_PUBLIC))
            }
            ECDHEStep.SHARED_SECRET -> {
                val r = result ?: runECDHE()
                if (!r.alice.publicKey.isInfinity) points.add(GraphPoint(r.alice.publicKey, "QA", PointType.ALICE_PUBLIC))
                if (!r.bob.publicKey.isInfinity) points.add(GraphPoint(r.bob.publicKey, "QB", PointType.BOB_PUBLIC))
                if (!r.sharedSecret.isInfinity) {
                    points.add(GraphPoint(r.sharedSecret, "S", PointType.SHARED_SECRET))
                }
            }
        }
        return points
    }

    /** ECDHE 전체 실행 */
    fun runECDHE(): ECDHEResult {
        val r = engine.runECDHE(curve, generator, alicePrivateKey, bobPrivateKey)
        result = r
        return r
    }

    fun getViewportX(): ClosedFloatingPointRange<Double> = -3.5..4.5
    fun getViewportY(): ClosedFloatingPointRange<Double> = -4.0..4.0

    /** 현재 단계의 construction 직선들 반환 (접선/할선) */
    fun getConstructionSteps(): List<ConstructionStep> {
        val steps = mutableListOf<ConstructionStep>()
        applyKeys()
        when (currentStep) {
            ECDHEStep.ALICE_KEYGEN -> {
                steps.addAll(engine.getConstructionSteps(curve, generator, alicePrivateKey, StepOwner.ALICE))
            }
            ECDHEStep.BOB_KEYGEN, ECDHEStep.EXCHANGE, ECDHEStep.SHARED_SECRET -> {
                steps.addAll(engine.getConstructionSteps(curve, generator, alicePrivateKey, StepOwner.ALICE))
                steps.addAll(engine.getConstructionSteps(curve, generator, bobPrivateKey, StepOwner.BOB))
            }
            else -> {}
        }
        return steps
    }

    /** 입력값 검증 및 적용 */
    fun applyKeys(): Boolean {
        val da = aliceInput.toIntOrNull()
        val db = bobInput.toIntOrNull()
        if (da == null || db == null || da < 1 || db < 1 || da > 20 || db > 20) {
            showError = true
            return false
        }
        showError = false
        alicePrivateKey = da
        bobPrivateKey = db
        // 키가 바뀌면 결과 리셋
        result = null
        return true
    }
}

// ---- 메인 스크린 컴포저블 ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ECDHEScreen() {
    val state = remember { ECDHEScreenState() }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ECDHE 세션키 생성",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isLandscape = maxWidth > maxHeight
            val horizontalPadding = 12.dp
            val availHeight = maxHeight

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "타원곡선: ${state.curve.equationStr()}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            ECCurveCanvas(
                                curve = state.curve,
                                points = state.getGraphPoints(),
                                viewportX = state.getViewportX(),
                                viewportY = state.getViewportY(),
                                constructionSteps = state.getConstructionSteps(),
                                canvasHeight = availHeight - 80.dp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StepIndicator(
                            steps = ECDHEStep.entries.toList(),
                            currentStep = state.currentStep,
                            onStepClick = { state.currentStep = it }
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(scrollState)
                        ) {
                            AnimatedContent(
                                targetState = state.currentStep,
                                transitionSpec = {
                                    fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                                },
                                label = "step_content"
                            ) { step ->
                                StepContent(
                                    step = step,
                                    state = state
                                )
                            }
                        }

                        ControlButtons(
                            state = state,
                            onPrev = {
                                val ordinal = state.currentStep.ordinal
                                if (ordinal > 0) {
                                    state.currentStep = ECDHEStep.entries[ordinal - 1]
                                }
                            },
                            onNext = {
                                val ordinal = state.currentStep.ordinal
                                if (ordinal < ECDHEStep.entries.size - 1) {
                                    val canProceed = if (state.currentStep == ECDHEStep.ALICE_KEYGEN ||
                                        state.currentStep == ECDHEStep.BOB_KEYGEN
                                    ) {
                                        state.applyKeys()
                                    } else true
                                    if (canProceed) {
                                        if (state.currentStep == ECDHEStep.BOB_KEYGEN) {
                                            state.runECDHE()
                                        }
                                        state.currentStep = ECDHEStep.entries[ordinal + 1]
                                    }
                                }
                            },
                            onReset = {
                                state.currentStep = ECDHEStep.PARAMS
                                state.aliceInput = "3"
                                state.bobInput = "2"
                                state.alicePrivateKey = 3
                                state.bobPrivateKey = 2
                                state.result = null
                                state.showError = false
                            }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = horizontalPadding, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    StepIndicator(
                        steps = ECDHEStep.entries.toList(),
                        currentStep = state.currentStep,
                        onStepClick = { state.currentStep = it }
                    )

                    // 그래프
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "타원곡선: ${state.curve.equationStr()}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            ECCurveCanvas(
                                curve = state.curve,
                                points = state.getGraphPoints(),
                                viewportX = state.getViewportX(),
                                viewportY = state.getViewportY(),
                                constructionSteps = state.getConstructionSteps(),
                                canvasHeight = minOf(300.dp, availHeight * 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 단계별 콘텐츠
                    AnimatedContent(
                        targetState = state.currentStep,
                        transitionSpec = {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                        },
                        label = "step_content"
                    ) { step ->
                        StepContent(
                            step = step,
                            state = state
                        )
                    }

                    // 제어 버튼
                    ControlButtons(
                        state = state,
                        onPrev = {
                            val ordinal = state.currentStep.ordinal
                            if (ordinal > 0) {
                                state.currentStep = ECDHEStep.entries[ordinal - 1]
                            }
                        },
                        onNext = {
                            val ordinal = state.currentStep.ordinal
                            if (ordinal < ECDHEStep.entries.size - 1) {
                                val canProceed = if (state.currentStep == ECDHEStep.ALICE_KEYGEN ||
                                    state.currentStep == ECDHEStep.BOB_KEYGEN
                                ) {
                                    state.applyKeys()
                                } else true
                                if (canProceed) {
                                    if (state.currentStep == ECDHEStep.BOB_KEYGEN) {
                                        state.runECDHE()
                                    }
                                    state.currentStep = ECDHEStep.entries[ordinal + 1]
                                }
                            }
                        },
                        onReset = {
                            state.currentStep = ECDHEStep.PARAMS
                            state.aliceInput = "3"
                            state.bobInput = "2"
                            state.alicePrivateKey = 3
                            state.bobPrivateKey = 2
                            state.result = null
                            state.showError = false
                        }
                    )
                }
            }
        }
    }
}

// ---- 구성 컴포넌트 ----

/** 단계 표시기 */
@Composable
private fun StepIndicator(
    steps: List<ECDHEStep>,
    currentStep: ECDHEStep,
    onStepClick: (ECDHEStep) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            val isActive = step == currentStep
            val isCompleted = step.ordinal < currentStep.ordinal
            val bgColor by animateColorAsState(
                targetValue = when {
                    isActive -> MaterialTheme.colorScheme.primary
                    isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                label = "step_bg"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onStepClick(step) }
                    .width(60.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        color = if (isActive || isCompleted) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = step.label,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

/** 단계별 메인 콘텐츠 */
@Composable
private fun StepContent(step: ECDHEStep, state: ECDHEScreenState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 단계 설명
            Text(
                text = "Step ${step.ordinal + 1}: ${step.description}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider()

            // 단계별 상세 내용
            when (step) {
                ECDHEStep.PARAMS -> ParamsContent(state)
                ECDHEStep.ALICE_KEYGEN -> AliceKeygenContent(state)
                ECDHEStep.BOB_KEYGEN -> BobKeygenContent(state)
                ECDHEStep.EXCHANGE -> ExchangeContent(state)
                ECDHEStep.SHARED_SECRET -> SharedSecretContent(state)
            }
        }
    }
}

/** Step 0: 파라미터 설정 */
@Composable
private fun ParamsContent(state: ECDHEScreenState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "1. 타원곡선(Elliptic Curve) 정의",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "곡선 방정식: ${state.curve.equationStr()}",
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "파라미터: a = ${state.curve.a.toInt()},  b = ${state.curve.b.toInt()}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "판별식 Δ = ${"%.1f".format(state.curve.discriminant)} (≠ 0 이므로 비특이 곡선)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text(
            text = "2. 생성점(Generator) G 선택",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "G = ${state.generator.fmt()}",
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50)
        )
        Text(
            text = "생성점 G는 곡선 위의 한 점으로, 모든 키 생성의 기준이 됩니다.",
            style = MaterialTheme.typography.bodyMedium
        )

        InfoBox(
            "📐 ECDHE는 타원곡선의 '이산 로그 문제(ECDLP)'의 어려움에 기반합니다. " +
                    "G에서 출발해 스칼라 곱셈 k·G는 쉽게 계산할 수 있지만, " +
                    "결과 점 Q에서 k를 역산하는 것은 사실상 불가능합니다."
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text(
            text = "3. 스칼라 곱셈 k·G의 원리",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "공개키 QA = dA·G는 '생성점 G를 개인키 dA번 더한다'는 의미입니다:",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "dA·G = G + G + ··· + G  (dA번)",
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "타원곡선에서 점의 덧셈은 기하학적으로 정의됩니다:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "• P + P (두 배): P를 지나는 접선 → 곡선과 교점 → x축 대칭\n" +
                    "• P + Q (합): P, Q를 잇는 할선 → 곡선과 교점 → x축 대칭",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "dA가 20 이상으로 커져도 'double-and-add' 알고리즘으로 빠르게 계산합니다:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "① dA를 이진수로 분해 (예: 13 → 1101₂)\n" +
                    "② G부터 시작해 매 단계 'doubling'(접선)으로 2배, 4배, 8배, … 점 생성\n" +
                    "③ 이진수의 1인 자리에 해당하는 점만 'add'(할선)로 누적 합산\n" +
                    "→ dA번 일일이 더할 필요 없이 log₂(dA)회 doubling + α로 계산 완료",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace
        )
    }
}

/** Step 1: 앨리스 키 생성 */
@Composable
private fun AliceKeygenContent(state: ECDHEScreenState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "앨리스(Alice)의 키 생성",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF2196F3)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("개인키 dA =", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.aliceInput,
                onValueChange = {
                    state.aliceInput = it
                    state.showError = false
                },
                modifier = Modifier.width(80.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = state.showError,
                supportingText = if (state.showError) {
                    { Text("1~20") }
                } else null
            )
        }

        if (state.applyKeys()) {
            val qa = state.engine.generateKeyPair(state.curve, state.generator, state.alicePrivateKey)
            Text(
                text = "공개키 QA = dA·G = ${qa.publicKey.fmt()}",
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2196F3)
            )
            Text(
                text = "계산: ${state.alicePrivateKey} × G = ${qa.publicKey.fmtShort()}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        InfoBox(
            "💡 앨리스는 개인키 dA를 무작위로 선택하고, 공개키 QA = dA·G를 계산합니다. " +
                    "개인키 dA는 앨리스만 알고 있으며 절대 공유되지 않습니다."
        )

        // 스칼라 곱셈 과정 표시
        Column {
            Text(
                text = "스칼라 곱셈 과정 (Double-and-Add):",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = buildScalarMultExplanation(state.alicePrivateKey, "QA"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Step 2: 밥 키 생성 */
@Composable
private fun BobKeygenContent(state: ECDHEScreenState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "밥(Bob)의 키 생성",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFFF5722)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("개인키 dB =", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.bobInput,
                onValueChange = {
                    state.bobInput = it
                    state.showError = false
                },
                modifier = Modifier.width(80.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = state.showError,
                supportingText = if (state.showError) {
                    { Text("1~20") }
                } else null
            )
        }

        if (state.applyKeys()) {
            val qb = state.engine.generateKeyPair(state.curve, state.generator, state.bobPrivateKey)
            Text(
                text = "공개키 QB = dB·G = ${qb.publicKey.fmt()}",
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF5722)
            )
            Text(
                text = "계산: ${state.bobPrivateKey} × G = ${qb.publicKey.fmtShort()}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        InfoBox(
            "💡 밥도 앨리스와 마찬가지로 개인키 dB를 무작위로 선택하고, 공개키 QB = dB·G를 계산합니다. " +
                    "밥의 개인키 dB도 밥만 알고 있습니다."
        )

        // 스칼라 곱셈 과정 표시
        Column {
            Text(
                text = "스칼라 곱셈 과정 (Double-and-Add):",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = buildScalarMultExplanation(state.bobPrivateKey, "QB"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Step 3: 공개키 교환 */
@Composable
private fun ExchangeContent(state: ECDHEScreenState) {
    state.applyKeys()
    val qa = state.engine.generateKeyPair(state.curve, state.generator, state.alicePrivateKey).publicKey
    val qb = state.engine.generateKeyPair(state.curve, state.generator, state.bobPrivateKey).publicKey

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "공개키 교환 (Public Key Exchange)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        // 교환 다이어그램
        ExchangeDiagram(
            aliceKey = qa.fmtShort(),
            bobKey = qb.fmtShort()
        )

        HorizontalDivider()

        Text(
            text = "앨리스 → 밥: QA = ${qa.fmtShort()}",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF2196F3)
        )
        Text(
            text = "밥 → 앨리스: QB = ${qb.fmtShort()}",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFFF5722)
        )

        InfoBox(
            "🔓 공개키 QA와 QB는 공개 채널(네트워크)을 통해 전송됩니다. " +
                    "공개키만으로는 개인키를 유추할 수 없습니다(ECDLP)."
        )
    }
}

/** Step 4: 공유 비밀 */
@Composable
private fun SharedSecretContent(state: ECDHEScreenState) {
    state.applyKeys()
    val result = state.runECDHE()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "공유 세션키 S 계산",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        // 앨리스 측 계산
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2196F3).copy(alpha = 0.08f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "앨리스의 계산",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "S = dA × QB",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "  = ${state.alicePrivateKey} × (${result.bob.publicKey.fmtShort()})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "  = ${result.sharedSecret.fmt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE91E63)
                )
            }
        }

        // 밥 측 계산
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFF5722).copy(alpha = 0.08f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "밥의 계산",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF5722)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "S = dB × QA",
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "  = ${state.bobPrivateKey} × (${result.alice.publicKey.fmtShort()})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "  = ${result.sharedSecret.fmt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE91E63)
                )
            }
        }

        // 동일함 확인
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF4CAF50).copy(alpha = 0.12f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("✅", fontSize = 24.sp)
                Column {
                    Text(
                        text = "동일한 세션키 S 달성!",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    Text(
                        text = "S = ${result.sharedSecret.fmt()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "앨리스와 밥만이 아는 공유 비밀키입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        InfoBox(
            "🔐 dA·QB = dA·(dB·G) = (dA·dB)·G = dB·(dA·G) = dB·QA\n" +
                    "스칼라 곱셈의 교환 법칙에 의해 두 계산 결과는 항상 같습니다.\n" +
                    "공유된 S를 세션키로 사용하여 대칭키 암호화를 수행합니다."
        )
    }
}

/** 교환 다이어그램 */
@Composable
private fun ExchangeDiagram(aliceKey: String, bobKey: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 앨리스
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("👤", fontSize = 32.sp)
                Text(
                    text = "Alice",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "dA: 비밀",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "QA: 공개",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF2196F3)
                )
            }

            // 화살표 영역
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // → QA
                Text(
                    text = "QA →",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFF2196F3)
                )
                Text(
                    text = "⬌",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "← QB",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFFFF5722)
                )
            }

            // 밥
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("👤", fontSize = 32.sp)
                Text(
                    text = "Bob",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF5722)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "dB: 비밀",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "QB: 공개",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFF5722)
                )
            }
        }
    }
}

/** 제어 버튼 */
@Composable
private fun ControlButtons(
    state: ECDHEScreenState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onPrev,
            enabled = state.currentStep.ordinal > 0
        ) {
            Text("← 이전")
        }

        OutlinedButton(onClick = onReset) {
            Text("처음부터")
        }

        Button(
            onClick = onNext,
            enabled = state.currentStep.ordinal < ECDHEStep.entries.size - 1
        ) {
            Text("다음 →")
        }
    }
}

/** 정보 박스 */
@Composable
private fun InfoBox(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

private fun buildScalarMultExplanation(k: Int, resultName: String): String {
    if (k <= 0) return "k = $k (정의되지 않음)"
    if (k == 1) return "1 × G = G → $resultName = G"

    val parts = mutableListOf<String>()
    val binary = k.toString(2)
    val bits = binary.reversed()

    parts.add("dA = $k (이진: ${binary})")
    parts.add("")

    var step = 1
    var doubledPointStr = "G"
    val accumulatedTerms = mutableListOf<String>()

    for ((idx, bit) in bits.withIndex()) {
        val termStr = if (idx == 0) "G" else "2^${idx}·G"
        val nextDoubledStr = "2^${idx+1}·G"

        if (bit == '1') {
            accumulatedTerms.add(termStr)
            val sumStr = accumulatedTerms.joinToString(" + ")
            parts.add("Step $step: (bit=1) $termStr → 결과에 더함  → $sumStr")
            step++
            if (idx < bits.length - 1) {
                parts.add("        : ${doubledPointStr} + ${doubledPointStr} = ${nextDoubledStr}  (double)")
            }
        } else {
            parts.add("Step $step: (bit=0) $termStr → skip")
            step++
            if (idx < bits.length - 1) {
                parts.add("        : ${doubledPointStr} + ${doubledPointStr} = ${nextDoubledStr}  (double)")
            }
        }
        doubledPointStr = nextDoubledStr
    }

    parts.add("")
    parts.add("$resultName = $k·G = ${accumulatedTerms.joinToString(" + ")}")
    return parts.joinToString("\n")
}
