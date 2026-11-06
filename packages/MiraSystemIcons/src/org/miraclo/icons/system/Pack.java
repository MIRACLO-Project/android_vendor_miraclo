/*
 * SPDX-FileCopyrightText: MIRACLO
 * SPDX-License-Identifier: Apache-2.0
 */

package org.miraclo.icons.system;

import android.app.Activity;
import android.os.Bundle;

/** Lets launchers find this icon pack; there is nothing to show, so it closes at once. */
public class Pack extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}
