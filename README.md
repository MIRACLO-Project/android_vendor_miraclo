<picture>
  <source media="(prefers-color-scheme: light)" srcset="https://raw.githubusercontent.com/MIRACLO-Project/MIRACLO/main/assets/header_light.png">
  <img alt="MIRACLO: Pure Android 17. Expressive where it counts." src="https://raw.githubusercontent.com/MIRACLO-Project/MIRACLO/main/assets/header_dark.png">
</picture>

# vendor/miraclo

Product configuration shared by every MIRACLO device.

- `config/version.mk`: version, codename (Aurora for Android 17) and release variant
- `config/common.mk`: properties every MIRACLO build carries; device products
  inherit it

MIRACLO is built on Qualcomm CLO (`LA.QSSI.17.0`). See `PORTING.md` (coming)
for bringing up a new device.
