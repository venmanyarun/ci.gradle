/*
 * (C) Copyright IBM Corporation 2026.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openliberty.tools.gradle.utils

/**
 * Utility methods for reading server.env files.
 */
public class ServerEnvUtil {

    /**
     * Reads a single key's value from a server.env file by parsing key=value lines directly.
     * Does not mutate the file. Returns null if the key is not found.
     * The value is returned as-is; callers are responsible for any normalisation needed.
     */
    public static String readEnvValue(File serverEnvFile, String key) {
        return serverEnvFile.withReader('UTF-8') { reader ->
            String line
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith('#')) {
                    String[] kv = line.split('=', 2)
                    if (kv.length == 2 && kv[0] == key) {
                        return kv[1]
                    }
                }
            }
            return null
        }
    }
}
