package com.vibe.terminal.data.ssh

/**
 * SSH 错误分析结果
 */
data class SshErrorInfo(
    val title: String,
    val description: String,
    val suggestions: List<String>,
    val technicalDetails: String,
    val errorCode: SshErrorCode
)

enum class SshErrorCode {
    ALGORITHM_NOT_SUPPORTED,
    AUTH_FAILED,
    CONNECTION_REFUSED,
    CONNECTION_TIMEOUT,
    HOST_UNREACHABLE,
    HOST_KEY_VERIFICATION,
    UNKNOWN
}

/**
 * SSH 错误分析器
 *
 * 将技术性的 SSH 错误转换为用户友好的提示信息
 */
object SshErrorAnalyzer {

    fun analyze(error: Throwable): SshErrorInfo {
        val message = error.message ?: ""
        val fullTrace = error.stackTraceToString()

        return when {
            // 算法不支持 (如 x25519, ed25519 等)
            message.contains("no such algorithm", ignoreCase = true) ||
            message.contains("algorithm", ignoreCase = true) && message.contains("not", ignoreCase = true) -> {
                val algorithm = extractAlgorithm(message)
                SshErrorInfo(
                    title = "加密算法不支持",
                    description = "服务器要求使用 $algorithm 算法，但当前环境不支持。",
                    suggestions = listOf(
                        "服务器可能使用了较新的加密算法",
                        "尝试在服务器端 sshd_config 中添加兼容的算法",
                        "检查服务器的 SSH 配置是否过于严格"
                    ),
                    technicalDetails = message,
                    errorCode = SshErrorCode.ALGORITHM_NOT_SUPPORTED
                )
            }

            // 认证失败
            message.contains("auth", ignoreCase = true) &&
            (message.contains("fail", ignoreCase = true) || message.contains("denied", ignoreCase = true)) -> {
                SshErrorInfo(
                    title = "认证失败",
                    description = "无法使用提供的凭据登录服务器。",
                    suggestions = listOf(
                        "检查用户名是否正确",
                        "检查密码或 SSH 密钥是否正确",
                        "确认服务器是否允许该认证方式",
                        "如果使用密钥，确认密钥格式是否正确 (OpenSSH 格式)"
                    ),
                    technicalDetails = message,
                    errorCode = SshErrorCode.AUTH_FAILED
                )
            }

            // 连接被拒绝
            message.contains("connection refused", ignoreCase = true) ||
            message.contains("refused", ignoreCase = true) -> {
                SshErrorInfo(
                    title = "连接被拒绝",
                    description = "服务器拒绝了连接请求。",
                    suggestions = listOf(
                        "确认服务器地址和端口是否正确",
                        "检查服务器上的 SSH 服务是否正在运行",
                        "检查防火墙是否阻止了连接",
                        "如果使用 Tailscale，确认设备是否在线"
                    ),
                    technicalDetails = message,
                    errorCode = SshErrorCode.CONNECTION_REFUSED
                )
            }

            // 连接超时
            message.contains("timeout", ignoreCase = true) ||
            message.contains("timed out", ignoreCase = true) -> {
                SshErrorInfo(
                    title = "连接超时",
                    description = "连接服务器时超时，服务器可能不可达。",
                    suggestions = listOf(
                        "检查网络连接是否正常",
                        "确认服务器地址是否正确",
                        "如果使用 VPN/Tailscale，确认 VPN 已连接",
                        "服务器可能处于休眠状态，尝试唤醒"
                    ),
                    technicalDetails = message,
                    errorCode = SshErrorCode.CONNECTION_TIMEOUT
                )
            }

            // 主机不可达
            message.contains("unreachable", ignoreCase = true) ||
            message.contains("no route", ignoreCase = true) ||
            message.contains("unknown host", ignoreCase = true) -> {
                SshErrorInfo(
                    title = "无法访问主机",
                    description = "无法找到或访问目标服务器。",
                    suggestions = listOf(
                        "检查服务器地址是否正确",
                        "确认网络连接正常",
                        "如果使用域名，检查 DNS 解析",
                        "如果使用 Tailscale，确认 MagicDNS 已启用"
                    ),
                    technicalDetails = message,
                    errorCode = SshErrorCode.HOST_UNREACHABLE
                )
            }

            // Host key 验证失败
            message.contains("host key", ignoreCase = true) ||
            message.contains("fingerprint", ignoreCase = true) -> {
                SshErrorInfo(
                    title = "主机密钥验证失败",
                    description = "服务器的身份无法验证，可能是首次连接或服务器密钥已更改。",
                    suggestions = listOf(
                        "如果是首次连接该服务器，这是正常的",
                        "如果服务器重装过系统，密钥会改变",
                        "警告：如果不确定，请勿继续，可能存在中间人攻击"
                    ),
                    technicalDetails = message,
                    errorCode = SshErrorCode.HOST_KEY_VERIFICATION
                )
            }

            // 未知错误
            else -> {
                SshErrorInfo(
                    title = "连接失败",
                    description = "连接服务器时发生错误。",
                    suggestions = listOf(
                        "检查网络连接",
                        "确认服务器地址和端口正确",
                        "查看详细错误信息了解更多"
                    ),
                    technicalDetails = if (message.isNotBlank()) message else fullTrace.take(500),
                    errorCode = SshErrorCode.UNKNOWN
                )
            }
        }
    }

    private fun extractAlgorithm(message: String): String {
        // 尝试从错误信息中提取算法名称
        val patterns = listOf(
            Regex("algorithm[:\\s]+([\\w-]+)", RegexOption.IGNORE_CASE),
            Regex("([\\w-]+)\\s+for provider", RegexOption.IGNORE_CASE),
            Regex("no such algorithm[:\\s]+([\\w-]+)", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            pattern.find(message)?.groupValues?.getOrNull(1)?.let {
                return it
            }
        }

        return "未知算法"
    }
}
