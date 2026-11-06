#
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
#

# Common MIRACLO product configuration. Device products inherit this after
# their own device.mk and set MIRACLO_BUILD to the device codename first.

MIRACLO_BUILD ?= $(TARGET_DEVICE)

include vendor/miraclo/config/version.mk

# Version properties, read by Settings (About phone, Android version page).
PRODUCT_SYSTEM_EXT_PROPERTIES += \
    ro.miraclo.version=$(MIRACLO_VERSION) \
    ro.miraclo.codename=$(MIRACLO_CODENAME) \
    ro.miraclo.build.date=$(MIRACLO_BUILD_DATE) \
    ro.miraclo.device=$(MIRACLO_BUILD)

# Google apps (vendor/miraclo/gapps/extract-gapps.sh makes them from Google's
# Pixel factory image). Build with WITH_GMS=false for the vanilla build.
WITH_GMS ?= true
ifeq ($(WITH_GMS),true)
$(call inherit-product-if-exists, vendor/miraclo/gapps/gapps.mk)
endif

# MIRACLO's own signing keys (vendor/miraclo-keys links to ~/.miraclo-certs on
# the build machine; never in git). Without them the build uses test keys.
ifneq ($(wildcard vendor/miraclo-keys/releasekey.x509.pem),)
PRODUCT_DEFAULT_DEV_CERTIFICATE := vendor/miraclo-keys/releasekey
endif

# Google key provisioning (RKP): without the server name, rkpdapp never fetches
# attestation keys and Google sign-in / Play checks fail on RKP-only phones.
PRODUCT_PRODUCT_PROPERTIES += \
    remote_provisioning.hostname=remoteprovisioning.googleapis.com

# Runtime permissions Google's core apps get on first boot.
ifeq ($(WITH_GMS),true)
PRODUCT_COPY_FILES += \
    vendor/miraclo/config/permissions/default-permissions-miraclo-gms.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/default-permissions/default-permissions-miraclo-gms.xml
endif

# Wallpaper & style (Android's picker), MIRACLO's stock wallpapers and the default wallpaper.
PRODUCT_PACKAGES += \
    ThemePicker \
    MiraCLOWallpapers \
    MiraCLOWallpaperOverlay

# MIRACLO boot animation: the light falls, draws the O and circles it while the phone starts.
PRODUCT_COPY_FILES += \
    vendor/miraclo/bootanimation/bootanimation-orbit.zip:$(TARGET_COPY_OUT_PRODUCT)/media/bootanimation.zip

# Mira Sans (Inter, SIL Open Font License 1.1): the system theme font.
PRODUCT_COPY_FILES += \
    vendor/miraclo/fonts/MiraSans-Variable.ttf:$(TARGET_COPY_OUT_PRODUCT)/fonts/MiraSans-Variable.ttf \
    vendor/miraclo/fonts/fonts_customization.xml:$(TARGET_COPY_OUT_PRODUCT)/etc/fonts_customization.xml
PRODUCT_PACKAGES += \
    MiraSansOverlay

# Mira sounds: notification, ringtone, alarm, lock, unlock and charging (our own recordings).
PRODUCT_COPY_FILES += \
    vendor/miraclo/sounds/MiraDrop.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/MiraDrop.ogg \
    vendor/miraclo/sounds/MiraAurora.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/MiraAurora.ogg \
    vendor/miraclo/sounds/MiraDawn.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/MiraDawn.ogg \
    vendor/miraclo/sounds/MiraLock.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/MiraLock.ogg \
    vendor/miraclo/sounds/MiraGlass.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/MiraGlass.ogg \
    vendor/miraclo/sounds/MiraRise.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/MiraRise.ogg
PRODUCT_PRODUCT_PROPERTIES += \
    ro.config.notification_sound=MiraDrop.ogg \
    ro.config.ringtone=MiraAurora.ogg \
    ro.config.alarm_alert=MiraDawn.ogg
PRODUCT_PACKAGES += \
    MiraSoundsOverlay

# Mira system app icons (an icon pack the launcher uses by default).
PRODUCT_PACKAGES += \
    MiraSystemIcons

# Mira Updater: over-the-air updates installed in the background (A/B).
PRODUCT_PACKAGES += \
    MiraUpdater
PRODUCT_COPY_FILES += \
    vendor/miraclo/packages/MiraUpdater/privapp-permissions-miraclo-updater.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/permissions/privapp-permissions-miraclo-updater.xml

# Mira Setup: MIRACLO's own first screens, before Google's (or Android's) setup.
PRODUCT_PACKAGES += \
    MiraSetup
PRODUCT_COPY_FILES += \
    vendor/miraclo/packages/MiraSetup/privapp-permissions-miraclo-setup.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/permissions/privapp-permissions-miraclo-setup.xml

# Status bar icon styles (Mira Studio › Status bar): Wi-Fi and mobile signal.
PRODUCT_PACKAGES += \
    MiraIconsColoros \
    MiraIconsIos \
    MiraIconsOneui

# Mira Dolby: the phone's Dolby Atmos engine, with the EasyEffects presets, per-app profiles
# and a Quick Settings tile.
PRODUCT_PACKAGES += \
    MiraDolby
PRODUCT_COPY_FILES += \
    vendor/miraclo/packages/MiraDolby/privapp-permissions-miraclo-dolby.xml:$(TARGET_COPY_OUT_SYSTEM_EXT)/etc/permissions/privapp-permissions-miraclo-dolby.xml

# Open-source apps in place of Android's old AOSP apps (vendor/miraclo/apps/apps.mk). They are
# arm64 builds, so the emulator keeps the AOSP apps.
ifneq ($(MIRACLO_BUILD),emu)
$(call inherit-product, vendor/miraclo/apps/apps.mk)
endif
