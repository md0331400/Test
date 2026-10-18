# Play Store legal pages

Deploy these two files as publicly reachable HTTPS URLs before submitting the app:

- `https://YOUR-DOMAIN/privacy-policy.html`
- `https://YOUR-DOMAIN/account-deletion.html`

For the existing Vercel project, upload these files to its public/static root and deploy. Then replace `KB_PRIVACY_POLICY_URL` and `KB_DELETION_URL` in `app/src/main/assets/index.html` with the real final URLs. Put the same URLs in Play Console's Privacy Policy and Account Deletion fields.

Do not submit the app until the URLs open in an incognito browser and the Data Safety form accurately matches the app's actual Firebase, FCM, Analytics, ImageKit, API and optional Drive practices.


Live URLs currently supplied for Play Console:

- Privacy Policy: https://kothabolbo-privacy-policy.vercel.app/
- Account deletion: https://kothabolbo-account-deletion.vercel.app/
