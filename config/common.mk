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

# MIRACLO boot animation (Descent).
PRODUCT_COPY_FILES += \
    vendor/miraclo/bootanimation/bootanimation.zip:$(TARGET_COPY_OUT_PRODUCT)/media/bootanimation.zip
