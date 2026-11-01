#
# SPDX-FileCopyrightText: MiraCLO
# SPDX-License-Identifier: Apache-2.0
#

# Common MiraCLO product configuration. Device products inherit this after
# their own device.mk and set MIRACLO_BUILD to the device codename first.

MIRACLO_BUILD ?= $(TARGET_DEVICE)

include vendor/miraclo/config/version.mk

# Version properties, read by Settings (About phone, Android version page).
PRODUCT_SYSTEM_EXT_PROPERTIES += \
    ro.miraclo.version=$(MIRACLO_VERSION) \
    ro.miraclo.codename=$(MIRACLO_CODENAME) \
    ro.miraclo.build.date=$(MIRACLO_BUILD_DATE) \
    ro.miraclo.device=$(MIRACLO_BUILD)
