# CardManager

CardManager 是一款本地优先的银行卡、账户、提醒任务和储蓄记录管理工具。

数据默认只保存在设备本机，不做云同步，不上传个人信息。

## 功能

- 管理实体卡、虚拟卡和纯账户
- 支持卡片分组、搜索、筛选和排序
- 记录卡面图、银行 Logo、卡种、卡组织、币种、尾号、有效期和备注
- 支持还款、保活、投资等日历任务
- 投资类任务可选择是否避开非交易日
- 储蓄罐支持流水记录和任务金额同步
- 数据页提供本地统计和图表
- 支持 `.cmbak` 加密备份导入导出
- 支持自定义字体、主题模式和底部 Tab 显示设置
- 支持 NFC 读取银行卡公开 EMV 信息，用于辅助填写卡片资料

## 当前版本

- Version: `1.13.1`
- VersionCode: `21`
- Database schema: `v8`

## 构建

使用 Android Studio 打开项目根目录，等待 Gradle Sync 完成后运行。

项目技术栈：

- Kotlin
- Jetpack Compose
- Room SQLite
- Gradle

## 备份说明

应用支持导出 `.cmbak` 加密备份，备份内容包括结构化数据、卡面图片、银行 Logo、设置和自定义字体。

可选设置备份密码。请妥善保存备份文件和密码，项目不提供云端找回能力。

## 隐私

CardManager 是本地优先应用，不提供账号系统，不主动上传用户数据。

NFC 功能只读取银行卡公开 EMV 信息，用于本地辅助填写，不具备支付能力。

## 支持开发

如果这个项目帮到你，可以通过下面的方式支持后续维护。

| 微信赞赏 | 支付宝 |
|---|---|
| <img src="app/src/main/res/drawable-nodpi/donation_wechat.jpg" width="260" alt="微信赞赏码"> | <img src="app/src/main/res/drawable-nodpi/donation_alipay.jpg" width="260" alt="支付宝赞赏码"> |

USDT TRC-20:

```text
TBzhjqcTLQVkKiyGY3bE94RQnoj6TAFndw
```

## 许可

本项目基于 Apache License 2.0 开源。

详见 [LICENSE](LICENSE)。
