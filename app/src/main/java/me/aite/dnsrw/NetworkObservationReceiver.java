/*
 * Copyright 2026 肖其顿 (XIAO QI DUN)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.aite.dnsrw;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

public final class NetworkObservationReceiver extends BroadcastReceiver {
    public static final String ACTION = "me.aite.dnsrw.action.NETWORK_OBSERVED";
    public static final String REQUEST_ACTION =
            "me.aite.dnsrw.action.REQUEST_NETWORK_OBSERVATIONS";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_IDS = "ids";
    public static final String EXTRA_LABELS = "labels";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction()) || getSentFromUid() != Process.SYSTEM_UID) {
            return;
        }

        String type = intent.getStringExtra(EXTRA_TYPE);
        String[] ids = intent.getStringArrayExtra(EXTRA_IDS);
        String[] labels = intent.getStringArrayExtra(EXTRA_LABELS);
        if ((ObservationStore.TYPE_WIFI.equals(type) || ObservationStore.TYPE_SIM.equals(type))
                && ids != null
                && labels != null
                && ids.length == labels.length) {
            ObservationStore.replace(context, type, ids, labels);
        }
    }
}
