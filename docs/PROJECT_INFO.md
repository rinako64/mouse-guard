# Mouse Guard LP — プロジェクト全情報

> お子様のお口ぽかん防止アプリ「Mouse Guard」のランディングページ。
> 2026年5月1日 公開。

---

## 🌐 公開URL

| ページ | URL |
|---|---|
| **メインLP** | **https://mouseguard.app** |
| プライバシーポリシー | https://mouseguard.app/privacy.html |
| 利用規約 | https://mouseguard.app/terms.html |
| 運営者情報 | https://mouseguard.app/company.html |
| 仮ドメイン(予備) | https://okuchi-pokan-lp.vercel.app |

---

## 🏢 運営者情報

| 項目 | 内容 |
|---|---|
| 屋号 | Office Supple |
| 所在地 | 東京都港区南青山2丁目2番15号 |
| 事業内容 | モバイルアプリケーションの企画・開発・運営 |
| 取扱アプリ | Mouse Guard(お口ぽかん防止アプリ) |
| 公式メール | **info@mouseguard.app**(→ wadaiiweb@gmail.com に転送) |

---

## 💻 技術構成

| レイヤ | 採用技術 |
|---|---|
| フロントエンド | Vite 8 + React 19 |
| ホスティング | Vercel (Hobby plan / 無料) |
| ドメイン管理 | Vercel (登録レジストラ・DNS) |
| メール転送 | ImprovMX Free |
| HTTPS | Vercel 自動発行(Let's Encrypt) |

### ローカル環境
- Node.js v22.21.1
- npm v10.9.4
- 場所: `C:\Users\81907\projects\okuchi-pokan-lp\`

---

## 🗂️ ディレクトリ構成

```
okuchi-pokan-lp/
├── src/
│   ├── App.jsx              ← LP本体(React)
│   ├── components/
│   │   ├── Icons.jsx        ← SVGアイコン
│   │   ├── Blobs.jsx        ← 背景blob
│   │   └── AppScreens.jsx   ← スマホ画面プレビュー
│   ├── index.css            ← 全体スタイル
│   └── main.jsx
├── public/
│   ├── assets/              ← ぽかろ画像・スクリーンショット
│   ├── privacy.html         ← プライバシーポリシー(静的)
│   ├── terms.html           ← 利用規約(静的)
│   ├── company.html         ← 運営者情報(静的)
│   ├── styles.css           ← 静的ページ用基本CSS
│   └── privacy.css          ← 静的ページ用追加CSS
├── index.html               ← Vite エントリ
├── vercel.json              ← キャッシュ&セキュリティヘッダ
├── vite.config.js
├── package.json
└── PROJECT_INFO.md          ← このファイル
```

---

## 📡 DNS レコード(Vercel管理)

| Type | Name | Value | Priority | TTL |
|---|---|---|---|---|
| MX | @ | mx1.improvmx.com | 10 | 60 |
| MX | @ | mx2.improvmx.com | 20 | 60 |
| TXT | @ | `v=spf1 include:spf.improvmx.com include:_spf.google.com ~all` | - | 60 |
| CAA | @ | (Vercel自動設定) | - | 60 |

確認用URL: https://vercel.com/rinako64s-projects/~/domains/mouseguard.app

---

## 📧 メール構成

```
誰かが info@mouseguard.app に送信
   ↓
ImprovMX のサーバーが受信(MXレコードに従う)
   ↓
wadaiiweb@gmail.com に転送
   ↓
Itoさんは Gmail で受信・閲覧
```

### ImprovMXダッシュボード
- URL: https://improvmx.com/
- ログイン: wadaiiweb@gmail.com (Google認証)
- エイリアス:
  - `info@mouseguard.app` → wadaiiweb@gmail.com
  - `*@mouseguard.app`(catch-all)→ wadaiiweb@gmail.com

### 制限(無料プラン)
- 25 エイリアスまで
- 送信は不可(Gmail "Send mail as" で代替推奨)

---

## 🔧 更新ワークフロー

### LPの内容を変更したい場合
```bash
cd C:\Users\81907\projects\okuchi-pokan-lp

# ローカルで動作確認(http://localhost:5173/)
npm run dev

# 本番デプロイ
vercel --prod
```

### スマホ実機で確認したい場合
```bash
npm run dev -- --host 0.0.0.0
# 同一Wi-Fi内のスマホから http://192.168.1.18:5173/ にアクセス
```

### よくある編集箇所
| やりたいこと | 編集ファイル |
|---|---|
| 見出し・本文を変える | `src/App.jsx` |
| 色を変える | `src/index.css`(`:root` のCSS変数) |
| プライバシー/規約の文言 | `public/privacy.html` / `public/terms.html` |
| 運営者情報を更新 | `public/company.html` |
| 画像差し替え | `public/assets/` |

---

## 💰 コスト

| 項目 | 年額 |
|---|---|
| ドメイン `mouseguard.app` | 約¥2,300($14.99) |
| Vercel Hobby plan | ¥0 |
| ImprovMX Free | ¥0 |
| HTTPS証明書 | ¥0(自動) |
| **合計** | **約¥2,300/年** |

ドメイン更新日: 2027年5月1日(自動更新オプション有)

---

## 🧰 管理者用URL集

| 用途 | URL |
|---|---|
| Vercelダッシュボード | https://vercel.com/rinako64s-projects |
| プロジェクト設定 | https://vercel.com/rinako64s-projects/okuchi-pokan-lp |
| ドメイン管理 | https://vercel.com/rinako64s-projects/~/domains/mouseguard.app |
| デプロイ履歴 | https://vercel.com/rinako64s-projects/okuchi-pokan-lp/deployments |
| ImprovMX | https://improvmx.com/ |
| Vercel CLI(課金) | https://vercel.com/teams/rinako64s-projects/settings/billing |

---

## 🎯 今後やるとよいこと(任意)

- [ ] Gmail で `info@mouseguard.app` から **送信(返信)** できるよう設定
  - 設定 → アカウント → 他のメールアドレスを追加
  - SMTP: smtp.gmail.com:587 / Gmailアプリパスワード
- [ ] Vercel の請求先住所を登録(警告解消用)
  - Office Supple / 東京都港区南青山2丁目2番15号
- [ ] Google Search Console に `mouseguard.app` を登録(検索流入計測)
- [ ] OGP画像(SNSシェア時のサムネ)を本物にする
  - 現状: `assets/pokaro.png`
  - 推奨: 1200×630pxのカスタム画像
- [ ] Google Analytics 4(または Plausible)を導入(アクセス解析)
- [ ] **アプリ本体公開後**: Play Storeリンクを差し替え
  - `src/App.jsx` 内の `href="#cta"` と `href="#"` を Play Store URLに変更

---

## 📜 デプロイ履歴

| 日付 | 内容 |
|---|---|
| 2026-05-01 | プロジェクト作成、Vite+React 化、Vercelデプロイ、`mouseguard.app` 取得・接続、ImprovMX 設定、`info@mouseguard.app` 開通 |

---

## 🔐 アカウント情報

| サービス | アカウント |
|---|---|
| Vercel | rinako64(Team: rinako64s-projects) |
| ImprovMX | wadaiiweb@gmail.com (Google認証) |
| メール転送先 | wadaiiweb@gmail.com |

---

## 困ったときの連絡先

このプロジェクトを次回 Claude に開いてもらいたい場合は、このファイルを最初に読んでもらえば全体把握できます。

```
C:\Users\81907\projects\okuchi-pokan-lp\PROJECT_INFO.md を読んでから作業を始めて
```
