/*
 * Copyright 2023 Responsive Computing, Inc.
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

package dev.responsive.example;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.apache.kafka.common.utils.Bytes;

public class Temp {

  static final Bytes METADATA_KEY
      = Bytes.wrap("_metadata".getBytes(StandardCharsets.UTF_8));

  private static ByteBuffer metadataKey(final int partition) {
    final ByteBuffer key = ByteBuffer.allocate(METADATA_KEY.get().length + Integer.BYTES);
    key.put(METADATA_KEY.get());
    key.putInt(partition);
    return key;
  }

  public static void main(String[] args) {
    System.out.println(HexFormat.of().formatHex(metadataKey(2).array()));
  }



}
