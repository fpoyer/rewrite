/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.semver;

import org.openrewrite.Validated;

public class Semver {
    private Semver() {
    }

    public static Validated validate(String toVersion, String metadataPattern) {
        return LatestRelease.build(toVersion, metadataPattern)
                .or(HyphenRange.build(toVersion, metadataPattern))
                .or(XRange.build(toVersion, metadataPattern))
                .or(TildeRange.build(toVersion, metadataPattern))
                .or(CaretRange.build(toVersion, metadataPattern));
    }
}
