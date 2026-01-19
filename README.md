# 🧼 Interest Tracker

**Because your money shouldn't be earning sins.**

Welcome to the **Interest Tracker**, the app that helps you identify that pesky Riba peeking into your account so you can promptly _Yeet™_ it to charity. **100% Muslim Friendly.**

## 🏦 Supported Banks

<details>
<summary>Click to view supported banks</summary>

- **Punjab National Bank (CSV)** 🦁
- **Kotak Mahindra Bank (CSV)** 🏦
- **State Bank of India (XLSX)** 🏦

</details>

_Have a different bank?_

- 📞 **Contact me** - (I might listen) ([Telegram](https://t.me/LuLu786) · [Email](mailto:message@zaid.qzz.io))
- 💻 **Raise a PR** (I will definitely listen)

## 🚀 Self Hosting on Vercel

[![Deploy with Vercel](https://vercel.com/button)](https://vercel.com/new/clone?repository-url=https%3A%2F%2Fgithub.com%2FBioHazard786%2Finterest-tracker)

Want to run this yourself? Click the button above (or the one in your imagination) and add these environment variables.

### 🔑 Environment Variables

You'll need these to get the party started:

- `DATABASE_URL`: Your Postgres database string.
- `BETTER_AUTH_SECRET`: Mash your keyboard valid link key.

**For Google Login (mandatory):**

- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`

🔗 **Where to get them:**

- [Better Auth Google OAuth Docs](https://www.better-auth.com/docs/authentication/google)
- [Google Cloud Console](https://console.cloud.google.com/apis/credentials)

### 🗄️ Database Setup

Before you can track any sins, you need to tell your database what they look like.

```bash
# Generate migrations (Create the blueprint)
npm run drizzle:generate

# Push changes directly (For when you like living on the edge)
npm run drizzle:push

# Migrate properly (For the responsible adults)
npm run drizzle:migrate
```

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

_Made with ❤️ and a hatred for interest._
