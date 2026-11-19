#
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
#
# The open-source apps Mira ships in place of Android's old AOSP apps. fetch-apps.py downloads
# them from each project's official releases (pinned in apps.lock) and writes prebuilt/.

ifeq ($(wildcard vendor/miraclo/apps/prebuilt/Android.bp),)
$(error Mira apps are missing: run vendor/miraclo/apps/fetch-apps.sh first)
endif

# Vanilla only: the GApps variant ships Google's own apps instead. The camera in both is the
# phone's own Xiaomi camera. Phone and Messages are the default phone and SMS apps.
ifneq ($(WITH_GMS),true)
PRODUCT_PACKAGES += \
    MiraFossifyContacts \
    MiraFossifyCalendar \
    MiraFossifyCalculator \
    MiraFossifyFileManager \
    MiraAves \
    MiraGramophone \
    MiraHeliBoard \
    MiraCromite \
    MiraBrowserDefaultOverlay \
    MiraFossifyPhone \
    MiraFossifyMessages \
    MiraFossifyClock \
    MiraVanillaRolesOverlay
endif

# Native libraries some apps store compressed, installed beside the app (written by fetch-apps.py).
-include vendor/miraclo/apps/prebuilt/jni.mk

