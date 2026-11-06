#!/bin/bash
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
# Run by build-release.sh. Pass --update to move every app to its newest official release.
set -e
exec python3 "$(dirname "$0")/fetch-apps.py" "$@"
