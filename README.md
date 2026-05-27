# B-SideLoader
Download, install and autoupdate your apps by grabbing APKs from the Internet! Basically [Obtainium](https://github.com/ImranR98/Obtainium), but made using Android Compose.
Compatible with Android 17-8.0.
## Features
- It has support for downloading and automatic updating APKs from GitHub repos and _Telegram channels_.
- It has integrated search for all sources.
- It can use special privileges for seamless installations (Shizuku (ADB or Root), Dhizuku, Sui).
- Also your private data is encrypted using AES-256 GCM key stored in android hardware key storage!
### Tech stack:
- Android Compose with Kotlin
- MVVM Architecture (MAD, modified version by Google)
- Hilt dependency injection (Based on Dagger)
- Room for apps database storage
- Coil for loading images
- Retrofit 3.0 and OkHTTP for GitHub REST API
- AndroidX Work Manager for background jobs
- Datastore Preferences for storing app settings
- TDlib stripped from [TelegramX](https://github.com/TGX-Android/tdlib)
- And shizuku, dhizuku, refine and hiddenapibypass for getting special privileges
### Currently in WIP state, because there are pending breaking changes in database.
If you want to compile by yourself, you must get your telegram api id and hash at https://my.telegram.org/apps. It's made to prevent abuse of dev's credentials.
Also, it's recommended to set idea.max.intellisense.filesize=5000 in idea.properties and invalidate caches for comfortable development.
#### Why B-side
_A B-side is a "flipside" song, originally referring to the secondary, often experimental or unpromoted track on the back of a physical 7-inch vinyl single._
_Sideloading is the installation of applications on a device from unofficial sources outside authorized app stores._
## Screenshots
| <img src="./readme-files/1.png" alt="Apps Page" />       | <img src="./readme-files/2.png" alt="Search page" />       | <img src="./readme-files/3.png" alt="Searching GitHub" />  |
| -------------------------------------------------------- | ---------------------------------------------------------- | -----------------------------------------------------------|
| <img src="./readme-files/4.png" alt="Tg forum topics" /> | <img src="./readme-files/5.png" alt="Tg about app page" /> | <img src="./readme-files/6.png" alt="Gh about app page" /> |
| <img src="./readme-files/7.png" alt="Settings Page" />   |
