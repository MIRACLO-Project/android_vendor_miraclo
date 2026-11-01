# vendor/miraclo

Product configuration shared by every MiraCLO device.

- `config/version.mk`: version, codename (Aurora for Android 17) and release variant
- `config/common.mk`: properties every MiraCLO build carries; device products
  inherit it

MiraCLO is built on Qualcomm CLO (`LA.QSSI.17.0`). See `PORTING.md` (coming)
for bringing up a new device.
