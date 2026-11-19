# 10 — Cloud Deployment: Vercel (frontend) + Railway (backend + MySQL)

```
Browser ──► your-app.vercel.app                     Vercel: React build
              └─ /api/* ──forwarded──► <backend>.up.railway.app    Railway: Spring Boot (backend/Dockerfile)
                                             └──► MySQL (private network)          Railway: MySQL
```

Visitors only ever use the Vercel address. Vercel forwards `/api/*` to Railway, so the session cookie and the CSRF protection work exactly as they do locally. The backend and the database talk over Railway's private network, so MySQL is not exposed to the internet.

> **Demo data only.** This setup gives no hospital-grade guarantees for backups or data location. Do not store real patients in it (see README, Security).

## 1. Railway: database

1. Sign in at railway.com with GitHub and choose **New Project → Deploy MySQL**.
2. When it is running, open the MySQL service → **Variables**. You don't need to copy anything; the backend refers to these variables by name in step 2.

## 2. Railway: backend

1. In the same project: **+ Create → GitHub Repo →** your `Hospital-Management-System` repository.
2. Open the new service → **Settings**:
   - **Root Directory:** `backend`. Railway then finds `backend/Dockerfile` and builds with it.
   - **Networking → Generate Domain**, target port **8080**. Note the address, e.g. `hms-backend-production.up.railway.app`.
3. **Variables:** add these (*Raw Editor* lets you paste them all at once). `${{MySQL.…}}` are Railway references to the database service; if you renamed it, use its name instead of `MySQL`.
   ```
   HMS_DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true
   HMS_DB_USERNAME=${{MySQL.MYSQLUSER}}
   HMS_DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
   HMS_BOOTSTRAP_ADMIN_USERNAME=sysadmin
   HMS_BOOTSTRAP_ADMIN_PASSWORD=<choose: 10+ characters, a letter and a number>
   SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES=.*
   ```
   - The bootstrap pair creates the **first administrator** the first time the backend starts on an empty database.
   - The last line makes the backend read visitors' real addresses from the forwarded headers, so the per-IP sign-in limit counts each visitor separately rather than lumping everyone behind Vercel together.
4. **Volume:** right-click the service → **Attach Volume**, mount path **`/data`**. Uploaded patient documents live in `/data/uploads`; without a volume they vanish at every redeploy.
5. **Deploy.** The first build takes a few minutes. When it finishes, open
   `https://<your-backend>.up.railway.app/api/v1/actuator/health` in a browser. It should say `{"status":"UP"}`.
   The tables are created automatically (Flyway) on that first start.
6. After the first successful start, **delete `HMS_BOOTSTRAP_ADMIN_PASSWORD`** from the variables. The administrator already exists and must change that password at first sign-in.

## 3. Vercel: point the frontend at the backend

1. In the repository, edit `frontend/vercel.json` and replace `YOUR-BACKEND.up.railway.app` with your Railway address from step 2.2. Keep the `https://` and `/api/:path*` parts.
2. Commit and push. Vercel redeploys by itself.
3. In the Vercel project → **Settings → General**, check that **Root Directory** is `frontend`, so Vercel finds `vercel.json`. The framework is detected as Vite and builds into `dist`.

## 4. Sign in

1. Open your Vercel address and sign in as `sysadmin` with the bootstrap password. You'll be asked to choose a new one.
2. In **Users**, create the staff accounts you want. The online database starts empty: no patients and none of your local accounts.
3. Optional: fill the online system with demo data (section 5).

## 5. Optional: demo data

A year of invented hospital activity (patients, appointments, consultations, lab tests, prescriptions, admissions, invoices, payments, staff attendance and leave), so the dashboards and reports have something to show.

1. Railway → backend service → **Variables**, add:
   ```
   HMS_DEMO_SEED=true
   HMS_DEMO_PATIENTS=1000
   ```
   `HMS_DEMO_PATIENTS` sets the size: 1000 patients brings about 6,600 appointments, 6,500 invoices and 1,100 admissions. Activity scales with it; 3000 is the full size.
2. Deploy. The app starts normally and fills the database in the background, about 5 minutes for 1000 patients. The deploy log ends with `Demo data seeded in … s: {…}` when it's done.
3. **Remove both variables** and deploy once more.

It runs once only (a marker in `system_settings`), and it refuses any database that already has patients, so it can never mix invented records into real ones. It needs a first user to exist, which is the case after section 4.

## 6. Optional: staff logins and sign-up

**Staff logins.** Gives about 40 of the demo employees a sign-in account in the role matching their job (12 doctors, 14 nurses, 4 receptionists, 3 lab staff, 3 pharmacists, 2 accountants, 2 HR managers). Doctor accounts are linked to their doctor profile and see their own appointments. Needs the demo data from section 5.

1. Railway → backend → **Variables**, add:
   ```
   HMS_DEMO_STAFF_ENABLED=true
   HMS_DEMO_STAFF_PASSWORD=<choose: 10+ characters, a letter and a number>
   ```
   Every staff login shares this password. Keep it private.
2. Deploy. The log shows `Demo staff accounts created (40): [...]` with the usernames (first.last).
3. **Remove both variables** and deploy again. It runs once only.

**Sign-up.** Visitors can request an account at `/sign-up` and choose a staff role (never an administrator role). The account stays **Pending** and cannot sign in until an administrator opens **Users**, filters by *Sign-up requests* and chooses **Approve** (or **Reject**). To give a different role, choose **Edit** before approving. At most 100 requests can wait at once.

## Troubleshooting

| What you see | Cause | Fix |
|---|---|---|
| Sign-in says "Something went wrong" | `/api` isn't reaching Railway | Open `https://<vercel-app>/api/v1/actuator/health`. If it isn't `UP`, check the address in `vercel.json` and that the Railway service is running |
| Health shows the Vercel 404 page | The `vercel.json` changes aren't deployed | Root Directory must be `frontend`; push again |
| Railway deploy log: `Communications link failure` | Database variables wrong | Check that the `${{MySQL.…}}` names match your MySQL service's name |
| Railway deploy log: `Unsupported Database: MySQL 9.x` | Railway's MySQL is newer than Flyway supports | Replace the database with a **MySQL 8** service (deploy the Docker image `mysql:8.0` with a volume) and update the variables |
| Railway deploy log: `does not meet the password policy` | Bootstrap password too weak | 10+ characters with a letter and a number; redeploy |
| Signed in, but reloading a page shows 404 | The SPA rewrite isn't deployed | Same as the Vercel 404 row above |
| Uploaded documents disappear after a deploy | No volume | Attach a volume at `/data` (step 2.4) |
