# 卡片管家 CardManager

卡片管家是一款本地优先的银行卡、账户、日历任务和小金库管理工具。它适合同时管理实体卡、虚拟卡、多币种账户、还款提醒、保活任务、定投和存款计划。

数据默认保存在本机，不做云同步，不上传个人信息。

当前版本：**2.6.0**

## 功能亮点

- 卡片管理：记录银行、卡种、卡组织、币种、有效期、状态、备注、卡面图片和银行 Logo。
- 分组浏览：支持卡片分组、折叠、排序、搜索、筛选、图册模式和列表模式。
- 日历任务：支持每月、每周、每季度、每 N 天、单次任务，并可关联卡片。
- 小金库：记录快速流水和投资项目，支持定投、存款、底仓、资金调整、归档冻结。
- 多币种：小金库支持 CNY / HKD / USD 切换和汇率折算。
- 非交易日：投资任务可自动判断非交易日，并支持顺延到下一个交易日。
- 数据统计：支持卡片状态、卡组织、币种、分组、任务频率、小金库等本地统计。
- 备份恢复：支持本地备份导入导出，覆盖卡片、任务、小金库、设置和图片资源。
- 资源包：支持标准 `.zip` 卡片资源包导入与管理，可扩展卡面和银行 Logo。
- 个性化：支持深色模式、自定义字体、卡面布局、底部 Tab 和数据页显示项调整。

## 2.6.0 更新

- 数据页新增信用卡额度总览，显示信用卡数量、折算总额度和银行额度分布。
- CNY/HKD/USD 额度按小金库基准币种和现有汇率统一折算。
- 已注销卡不计入统计，未填写额度的信用卡仍计入数量。
- 数据总览设置可关闭额度面板，并可切换按卡号或按银行显示；逐卡模式显示卡片尾号。
- 数据库 schema 保持 v12。


## ZIP 资源包格式

卡片资源包是标准 `.zip` 文件，根目录必须直接包含 `manifest.json`，不能在外层再套一层文件夹：

```text
manifest.json
images/
  card-001.png
logos/
  bank-001.png
failed-images.json
failed-logos.json
```

最小 `manifest.json` 示例：

```json
{
  "format": "cardmanager-resource-pack",
  "formatVersion": 1,
  "id": "example-bank-cards",
  "name": "Example Bank Cards",
  "version": "2026.07",
  "items": [
    {
      "id": "example-card-001",
      "name": "Example Platinum Card",
      "bank": "Example Bank",
      "network": "VISA",
      "cardCategory": "信用卡",
      "currency": "USD",
      "image": "images/card-001.png",
      "bankLogo": "logos/bank-001.png"
    }
  ]
}
```

- `format` 固定为 `cardmanager-resource-pack`，`formatVersion` 当前为 `1`。
- `id` 只允许字母、数字、点、下划线和连字符；同一 `id` 的新 ZIP 会覆盖旧资源包。
- `image` 必须位于 `images/`；可选 `bankLogo` 必须位于 `logos/`。
- 路径使用 `/`，不能使用绝对路径、盘符、空段、`.` 或 `..`。
- 支持图片后缀：`.png`、`.jpg`、`.jpeg`、`.webp`。
- 单包最多 5000 个文件/清单项目，`manifest.json` 最大 8 MB，单张图片最大 24 MB，总解压大小最大 512 MB。

## 下载

请到 [Releases](https://github.com/ayyy7128/CardManager/releases) 下载最新 APK。

更新前建议先在应用设置中导出本地备份。

## 隐私说明

卡片管家以本地存储为主，不提供云同步服务。你录入的卡片、任务、图片和备份数据默认只保存在自己的设备或你主动导出的备份文件中。

应用可能在以下场景访问网络：

- 获取节假日/非交易日数据。
- 获取汇率数据。
- 用户主动访问 GitHub、下载页面或其他外部链接。

## 支持开发

如果这个项目刚好帮到你，可以在应用内“设置 - 关于”里找到赞赏入口，也可以使用下面的赞赏码支持后续维护。

<p>
  <img src="app/src/main/res/drawable-nodpi/donation_alipay.jpg" alt="支付宝赞赏码" width="220" />
  <img src="app/src/main/res/drawable-nodpi/donation_wechat.jpg" alt="微信赞赏码" width="220" />
</p>

USDT TRC-20：

```text
TBzhjqcTLQVkKiyGY3bE94RQnoj6TAFndw
```

## 开源许可

本项目基于 [Apache License 2.0](LICENSE) 开源。
