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

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import java.util.LinkedHashSet;
import java.util.Set;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class DnsRwApplication extends Application {
    public interface ServiceListener {
        void onServiceChanged(XposedService service);
    }

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Set<ServiceListener> LISTENERS = new LinkedHashSet<>();
    private static XposedService service;

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService boundService) {
                setService(boundService);
            }

            @Override
            public void onServiceDied(XposedService deadService) {
                if (service == deadService) {
                    setService(null);
                }
            }
        });
    }

    public static void addServiceListener(ServiceListener listener) {
        LISTENERS.add(listener);
        listener.onServiceChanged(service);
    }

    public static void removeServiceListener(ServiceListener listener) {
        LISTENERS.remove(listener);
    }

    private static void setService(XposedService newService) {
        service = newService;
        MAIN_HANDLER.post(() -> {
            for (ServiceListener listener : Set.copyOf(LISTENERS)) {
                listener.onServiceChanged(newService);
            }
        });
    }
}
