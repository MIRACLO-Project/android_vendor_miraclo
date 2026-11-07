# Bring MIRACLO to your device

MIRACLO is built from Qualcomm's CLO source for Android 17. Any Snapdragon device with a working Android 17 device tree can carry it. This guide is for maintainers who want to build MIRACLO for their device.

## What you need

- A device tree, vendor tree and kernel (or prebuilt kernel) that boot Android 17 on your device.
- A Linux build machine (64 GB RAM recommended, 400 GB free SSD).
- Your own signing keys. Official MIRACLO keys are never shared; every maintainer signs with their own.

## 1. Sync

```bash
repo init -u https://github.com/MIRACLO-Project/manifest -b aurora --git-lfs
```

Add your device in `.repo/local_manifests/<device>.xml`:

```xml
<manifest>
  <project path="device/<brand>/<device>" name="<your org>/android_device_<brand>_<device>" remote="github" revision="<branch>" />
  <project path="vendor/<brand>/<device>" name="<your org>/android_vendor_<brand>_<device>" remote="github" revision="<branch>" />
  <project path="kernel/<brand>/<device>" name="<your org>/android_kernel_<brand>_<device>" remote="github" revision="<branch>" />
</manifest>
```

Then `repo sync -c -j16 --force-sync --no-clone-bundle --no-tags`.

Apply the build patches once after every sync (they change repositories outside MIRACLO: release-keys tag, boot-jar list):

```bash
for p in vendor/miraclo/patches/*/*.patch; do d=$(basename $(dirname $p) | tr _ /); git -C $d apply $p; done
```

## 2. Product file

Copy `vendor/miraclo/config/miraclo_device.mk.example` to your device tree as `miraclo_<device>.mk`, fill in the names, and list it in your `AndroidProducts.mk`:

```make
PRODUCT_MAKEFILES := $(LOCAL_DIR)/miraclo_<device>.mk
```

`device/xiaomi/marble` (`miraclo_marble.mk`) is the reference.

## 3. Build

```bash
# GApps (Google's own apps from a Pixel factory image) or Vanilla
DEVICE=<device> DEVICE_NAME="<Marketing name>" bash vendor/miraclo/tools/build-release.sh <Pixel factory image URL or zip>
DEVICE=<device> DEVICE_NAME="<Marketing name>" VARIANT=vanilla bash vendor/miraclo/tools/build-release.sh
```

The fastboot package lands in `~/release/`. For a recovery package (payload OTA zip that installs from OrangeFox or TWRP), run this in the same tree after the release build:

```bash
source build/envsetup.sh
lunch miraclo_<device>-cp2a-user
m otapackage
```

The zip is in `out/target/product/<device>/`. Firmware is packed only when your device tree provides `stock/firmware/`.

Device features that depend on hardware stay off unless your device has them: Mira Dolby needs a Dolby Atmos engine in the vendor, and the Xiaomi camera only applies to Xiaomi devices.

## Your own signing keys

Create them once with Android's `development/tools/make_key` (releasekey, platform, shared, media, networkstack, sdk_sandbox, bluetooth, nfc) and keep them outside every repository. Never publish them, and keep using the same keys for every build so users can update without wiping.

## Kernel

Use your device's own kernel. The `aurora` scripts in `vendor/miraclo/kernel` are for marble; they show how MIRACLO builds Google's official GKI source and checks every vendor driver against it before release.

## Official builds

To become an official MIRACLO maintainer:

- Keep your trees public, with clean history and clear commit messages.
- Ship `user` builds signed with your own private keys, never test keys.
- No keybox, no Play Integrity or attestation spoofing, nothing taken from other ROMs.
- Every MIRACLO feature must work, or be hidden on your device if the hardware lacks it.
- Publish a changelog and SHA-256 checksums with every release, and test every build on your own device before release.
- Report and fix device bugs; MIRACLO-wide bugs go to the MIRACLO repositories.

Contact PSP on the MIRACLO Telegram group to apply.
