package com.example.ecdhe.ecdhe

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ECDHE 시각화를 위한 타원곡선 암호 수학 엔진.
 *
 * 실제 ECDHE는 유한체 Fp 위에서 동작하지만, 시각적 이해를 위해
 * 실수 ℝ 위에서 동작하는 곡선을 사용합니다.
 */
data class ECPoint(val x: Double, val y: Double) {
    companion object {
        /** 무한원점 (identity element) */
        val INFINITY = ECPoint(Double.NaN, Double.NaN)
    }

    val isInfinity: Boolean get() = x.isNaN() && y.isNaN()

    /** 특정 소수점 자리까지 문자열 표현 */
    fun fmt(decimals: Int = 4): String = if (isInfinity) "O" else "(${fmtNum(x, decimals)}, ${fmtNum(y, decimals)})"

    /** 짧은 표현 (x 좌표만) */
    fun fmtShort(decimals: Int = 4): String = if (isInfinity) "O" else fmtNum(x, decimals)

    private fun fmtNum(v: Double, d: Int): String {
        return if (abs(v) < 1e-10) "0" else "%.${d}f".format(v).trimEnd('0').trimEnd('.')
    }
}

/**
 * 타원곡선: y² = x³ + ax + b (ℝ 위)
 *
 * @property a 계수 a
 * @property b 계수 b
 */
data class EllipticCurve(val a: Double, val b: Double) {
    /** 판별식: -16(4a³ + 27b²) ≠ 0이어야 비특이 곡선 */
    val discriminant: Double get() = -16.0 * (4.0 * a * a * a + 27.0 * b * b)

    /** 곡선 위의 점인지 확인 */
    fun isOnCurve(p: ECPoint): Boolean {
        if (p.isInfinity) return true
        val rhs = p.x * p.x * p.x + a * p.x + b
        val lhs = p.y * p.y
        return abs(lhs - rhs) < 1e-6
    }

    /** 주어진 x에 대한 y² 값 (음수면 곡선 위에 없는 x) */
    fun ySquared(x: Double): Double = x * x * x + a * x + b

    /** 점 덧셈 P + Q */
    fun add(p: ECPoint, q: ECPoint): ECPoint {
        if (p.isInfinity) return q
        if (q.isInfinity) return p

        val (x1, y1) = p
        val (x2, y2) = q

        // P + (-P) = O
        if (abs(x1 - x2) < 1e-10 && abs(y1 + y2) < 1e-10) return ECPoint.INFINITY

        val s: Double = if (abs(x1 - x2) > 1e-10) {
            // P + Q (P ≠ Q): 기울기 = (y2 - y1) / (x2 - x1)
            (y2 - y1) / (x2 - x1)
        } else {
            // 점 두배 P + P: 기울기 = (3x1² + a) / (2y1)
            (3.0 * x1 * x1 + a) / (2.0 * y1)
        }

        val x3 = s * s - x1 - x2
        val y3 = s * (x1 - x3) - y1
        return ECPoint(x3, y3)
    }

    /** 스칼라 곱셈 k·P (double-and-add) */
    fun scalarMultiply(p: ECPoint, k: Int): ECPoint {
        if (k == 0 || p.isInfinity) return ECPoint.INFINITY

        var result = ECPoint.INFINITY
        var addend = p
        var n = if (k < 0) -k else k

        while (n > 0) {
            if (n and 1 == 1) {
                result = add(result, addend)
            }
            addend = add(addend, addend)
            n = n shr 1
        }
        return result
    }

    /** 곡선 방정식 y² = x³ + ax + b의 텍스트 표현 */
    fun equationStr(): String {
        fun term(coeff: Double, varName: String, degree: Int): String {
            if (coeff == 0.0) return ""
            val sign = if (coeff < 0) " - " else " + "
            val absV = abs(coeff)
            return when {
                degree == 0 -> "$sign${fmtAbs(coeff)}"
                degree == 1 && absV == 1.0 -> "$sign$varName"
                degree == 1 -> "$sign${fmtAbs(coeff)}$varName"
                absV == 1.0 -> "$sign$varName"
                else -> "$sign${fmtAbs(coeff)}$varName"
            }
        }
        val x3 = when {
            a == 0.0 && b == 0.0 -> "y² = x³"
            b == 0.0 -> "y² = x³${term(a, "x", 1)}"
            a == 0.0 -> "y² = x³${term(b, "", 0)}"
            else -> "y² = x³${term(a, "x", 1)}${term(b, "", 0)}"
        }
        return x3
    }

    private fun fmtAbs(v: Double): String {
        return if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)
    }
}

/**
 * ECDHE 키 쌍
 */
data class ECDHEKeyPair(
    val privateKey: Int,
    val publicKey: ECPoint
)

/**
 * ECDHE 세션키 생성 결과
 */
data class ECDHEResult(
    val curve: EllipticCurve,
    val generator: ECPoint,
    val alice: ECDHEKeyPair,
    val bob: ECDHEKeyPair,
    val sharedSecret: ECPoint
)

/** construction line의 주인 */
enum class StepOwner { ALICE, BOB }

/**
 * 스칼라 곱셈의 한 construction step (접선 또는 할선)
 * @param label 표시 라벨 (예: "2×G", "G+2G")
 * @param point1 첫 번째 점 (접선인 경우 접점)
 * @param point2 두 번째 점 (접선이면 point1과 동일)
 * @param slope 직선의 기울기
 * @param intercept 직선의 y절편
 * @param isTangent 접선 여부 (false면 할선)
 * @param reflectedPoint 곡선과의 세 번째 교점 (반사 전)
 * @param resultPoint 연산 결과 (= reflectedPoint를 x축 반사)
 * @param owner 주인 (ALICE 또는 BOB)
 */
data class ConstructionStep(
    val label: String,
    val point1: ECPoint,
    val point2: ECPoint,
    val slope: Double,
    val intercept: Double,
    val isTangent: Boolean,
    val reflectedPoint: ECPoint,
    val resultPoint: ECPoint,
    val owner: StepOwner
)

/**
 * ECDHE 엔진 - 세션키 생성 과정을 관리
 */
class ECDHEEngine {
    // 기본 곡선: y² = x³ - x + 1
    // G = (0, 1)
    val defaultCurve = EllipticCurve(a = -1.0, b = 1.0)
    val defaultGenerator = ECPoint(0.0, 1.0)

    /** 키 쌍 생성 */
    fun generateKeyPair(curve: EllipticCurve, generator: ECPoint, privateKey: Int): ECDHEKeyPair {
        require(privateKey > 0) { "privateKey must be positive" }
        val publicKey = curve.scalarMultiply(generator, privateKey)
        return ECDHEKeyPair(privateKey, publicKey)
    }

    /** 공유 비밀 계산: S = privateKey * otherPublicKey */
    fun computeSharedSecret(curve: EllipticCurve, privateKey: Int, otherPublicKey: ECPoint): ECPoint {
        return curve.scalarMultiply(otherPublicKey, privateKey)
    }

    /** 전체 ECDHE 세션키 생성 */
    fun runECDHE(
        curve: EllipticCurve = defaultCurve,
        generator: ECPoint = defaultGenerator,
        alicePrivateKey: Int = 3,
        bobPrivateKey: Int = 2
    ): ECDHEResult {
        val alice = generateKeyPair(curve, generator, alicePrivateKey)
        val bob = generateKeyPair(curve, generator, bobPrivateKey)

        val aliceShared = computeSharedSecret(curve, alicePrivateKey, bob.publicKey)
        val bobShared = computeSharedSecret(curve, bobPrivateKey, alice.publicKey)

        // 공유 비밀이 같은지 검증
        require(abs(aliceShared.x - bobShared.x) < 1e-6 && abs(aliceShared.y - bobShared.y) < 1e-6) {
            "Shared secrets do not match!"
        }

        return ECDHEResult(curve, generator, alice, bob, aliceShared)
    }

    /** 작은 정수 곱셈의 double-and-add 단계별 중간 결과 반환 (시각화용) */
    fun getMultiplicationSteps(curve: EllipticCurve, generator: ECPoint, k: Int): List<ECPoint> {
        if (k <= 0) return emptyList()
        val steps = mutableListOf<ECPoint>()
        var mid = ECPoint.INFINITY
        var ad = generator
        var remaining = k
        while (remaining > 0) {
            if (remaining and 1 == 1) {
                mid = curve.add(mid, ad)
                steps.add(mid)
            }
            ad = curve.add(ad, ad)
            remaining = remaining shr 1
        }
        return steps
    }

    /**
     * 스칼라 곱셈 k·G의 기하학적 construction steps 반환.
     * 각 step은 접선( doubling) 또는 할선(addition)과 그 결과를 포함.
     */
    fun getConstructionSteps(curve: EllipticCurve, generator: ECPoint, k: Int, owner: StepOwner): List<ConstructionStep> {
        if (k <= 1) return emptyList()

        val steps = mutableListOf<ConstructionStep>()
        var result = ECPoint.INFINITY
        var addend = generator
        var n = k
        var stepNum = 1

        fun fmtP(p: ECPoint): String = p.fmtShort(2)

        while (n > 0) {
            val bit = n and 1

            if (bit == 1) {
                if (!result.isInfinity) {
                    val isTan = abs(result.x - addend.x) < 1e-10 && abs(result.y - addend.y) < 1e-10
                    val s = if (isTan) {
                        (3.0 * result.x * result.x + curve.a) / (2.0 * result.y)
                    } else {
                        (addend.y - result.y) / (addend.x - result.x)
                    }
                    val intercept = result.y - s * result.x
                    val newResult = curve.add(result, addend)
                    val reflected = ECPoint(newResult.x, -newResult.y)

                    val label = if (isTan) "Step$stepNum: 2×${fmtP(result)}"
                    else "Step$stepNum: ${fmtP(result)}+${fmtP(addend)}"

                    steps.add(ConstructionStep(
                        label = label,
                        point1 = result,
                        point2 = addend,
                        slope = s,
                        intercept = intercept,
                        isTangent = isTan,
                        reflectedPoint = reflected,
                        resultPoint = newResult,
                        owner = owner
                    ))
                    stepNum++
                }
                result = curve.add(result, addend)
            }

            n = n shr 1

            if (n > 0) {
                val s = (3.0 * addend.x * addend.x + curve.a) / (2.0 * addend.y)
                val intercept = addend.y - s * addend.x
                val newAddend = curve.add(addend, addend)
                val reflected = ECPoint(newAddend.x, -newAddend.y)

                steps.add(ConstructionStep(
                    label = "Step$stepNum: 2×${fmtP(addend)}",
                    point1 = addend,
                    point2 = addend,
                    slope = s,
                    intercept = intercept,
                    isTangent = true,
                    reflectedPoint = reflected,
                    resultPoint = newAddend,
                    owner = owner
                ))
                addend = newAddend
                stepNum++
            }
        }
        return steps
    }
}
