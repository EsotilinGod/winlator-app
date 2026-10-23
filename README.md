<p align="center">
	<img src="logo.png" width="376" height="128" alt="Winlator Logo" />
</p>

# Winlator

Winlator is an Android application that lets you to run Windows (x86_64) applications with Wine and Box86/Box64.<br>
This repository stores the latest updates for the Winlator app source.<br>
For more information and releases, please visit the main repository: https://github.com/brunodev85/winlator

# Fork note: Mali / Xiaomi 14T (`mali-14t` branch)

This fork is an experimental adaptation of Winlator 11.2 for Mali/Immortalis GPUs (Vortek/Vulkan fixes, memory handling, HUD and sync improvements). It keeps the `com.winlator` application id, so it cannot be installed alongside the official app, and it is signed with a different key. Back up your containers before replacing an existing installation.

Details are in [MALI-14T.md](MALI-14T.md) and the notes under [docs/](docs/). Nothing here has been validated on physical hardware yet.

## Running the regression checks

```
tests/run_all.sh
```

This needs python3, a JDK and a C compiler. It runs the same host-side checks as the CI workflow (mock Vulkan driver, JVM logic); it does not run Android code or a real GPU.

# Credits and Third-party apps

- GLIBC Patches by [Termux Pacman](https://github.com/termux-pacman/glibc-packages)
- Wine ([winehq.org](https://www.winehq.org/))
- Box86/Box64 by [ptitseb](https://github.com/ptitSeb)
- Mesa (Turnip/Zink/VirGL) ([mesa3d.org](https://www.mesa3d.org))
- DXVK ([github.com/doitsujin/dxvk](https://github.com/doitsujin/dxvk))
- VKD3D ([gitlab.winehq.org/wine/vkd3d](https://gitlab.winehq.org/wine/vkd3d))
- CNC DDraw ([github.com/FunkyFr3sh/cnc-ddraw](https://github.com/FunkyFr3sh/cnc-ddraw))

Special thanks to all the developers involved in these projects.<br>
Thank you to all the people who believe in this project.