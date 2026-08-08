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

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class ObservationStore {
    public static final String TYPE_WIFI = "wifi";
    public static final String TYPE_SIM = "sim";

    private static final String PREFERENCES = "network_observations";
    private static final String SEPARATOR = ":";

    private ObservationStore() {
    }

    public static SharedPreferences preferences(Context context) {
        Context storageContext = context.createDeviceProtectedStorageContext();
        return storageContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public static void replace(
            Context context,
            String type,
            String[] ids,
            String[] labels
    ) {
        SharedPreferences preferences = preferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        String prefix = type + SEPARATOR;
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(prefix)) {
                editor.remove(key);
            }
        }
        for (int index = 0; index < ids.length; index++) {
            editor.putString(prefix + ids[index], labels[index]);
        }
        editor.apply();
    }

    public static Map<String, String> load(Context context, String type) {
        String prefix = type + SEPARATOR;
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, ?> entry : preferences(context).getAll().entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getValue() instanceof String label) {
                sorted.put(entry.getKey().substring(prefix.length()), label);
            }
        }
        return new LinkedHashMap<>(sorted);
    }
}
