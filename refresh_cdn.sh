#!/bin/bash

# ================= 配置区域 =================
SECRET_ID="${TENCENT_SECRET_ID}"
SECRET_KEY="${TENCENT_SECRET_KEY}"
CDN_DOMAIN="cdn.dingfengbo.top"  # 你的CDN加速域名
# ===========================================

# 检查是否传入了需要刷新的文件路径
if [ $# -eq 0 ]; then
    echo "用法: $0 <文件路径1> <文件路径2> ..."
    exit 1
fi

# 构造 URL 列表参数
URL_PARAMS=""
for FILE in "$@"; do
    # 提取文件名并拼接完整的 CDN URL
    FILENAME=$(basename "$FILE")
    URL_PARAMS+="&Urls.N=$((N++))=https://${CDN_DOMAIN}/media/images/books/${FILENAME}"
done

# 腾讯云 API 签名计算 (简化版，适用于基础调用)
TIMESTAMP=$(date +%s)
NONCE=$((RANDOM))
PARAMS="Action=PurgeUrlsCache&Nonce=${NONCE}&Region=ap-guangzhou&SecretId=${SECRET_ID}&Timestamp=${TIMESTAMP}&Version=2018-06-06${URL_PARAMS}"

# 生成签名 (使用 HMAC-SHA256)
SIGN_STR=$(echo -n "GETcdn.tencentcloudapi.com/?${PARAMS}" | openssl dgst -hmac "${SECRET_KEY}" -sha256 | awk '{print $2}')
SIGN=$(echo -n "${SIGN_STR}" | base64)

# 发起 API 请求
API_URL="https://cdn.tencentcloudapi.com/?${PARAMS}&Signature=${SIGN}"
RESPONSE=$(curl -s "${API_URL}")

# 输出结果
echo "腾讯云CDN刷新响应: $RESPONSE"