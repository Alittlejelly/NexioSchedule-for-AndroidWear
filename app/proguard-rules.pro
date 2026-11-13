# R8 规则（proguard-android-optimize.txt 基础上追加）
#
# 全工程没有反射调用（已核查 Class.forName / java.lang.reflect 均无），
# JSON 走 android.os 自带的 org.json，BLE 是纯 API 调用，
# Compose / Wear Compose 库自带 consumer rules，一般无需额外 keep。
#
# 若后续引入反射类库（如 kotlinx.serialization、Room），在这里补对应 keep 规则。
