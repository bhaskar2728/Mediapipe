// Copyright 2021 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.mediapipe.solutions.hands;

import android.content.Context;
import com.google.common.collect.ImmutableList;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList;
import com.google.mediapipe.formats.proto.ClassificationProto.Classification;
import com.google.mediapipe.framework.MediaPipeException;
import com.google.mediapipe.framework.Packet;
import com.google.mediapipe.solutionbase.ErrorListener;
import com.google.mediapipe.solutionbase.ImageSolutionBase;
import com.google.mediapipe.solutionbase.OutputHandler;
import com.google.mediapipe.solutionbase.ResultListener;
import com.google.mediapipe.solutionbase.SolutionInfo;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * MediaPipe Hands Solution API.
 *
 * <p>MediaPipe Hands processes a {@link TextureFrame} or a {@link Bitmap} and returns the hand
 * landmarks and handedness (left v.s. right hand) of each detected hand. Please refer to
 * https://solutions.mediapipe.dev/hands#android-solution-api for usage examples.
 */
public class Hands extends ImageSolutionBase {
  private static final String TAG = "Hands";

  private static final String NUM_HANDS = "num_hands";
  private static final String SOLUTION_GRAPH_NAME = "hand_landmark_tracking_gpu_image.binarypb";
  private static final String IMAGE_INPUT_STREAM = "image";
  private static final ImmutableList<String> OUTPUT_STREAMS =
      ImmutableList.of("multi_hand_landmarks", "multi_handedness", "image");
  private static final int LANDMARKS_INDEX = 0;
  private static final int HANDEDNESS_INDEX = 1;
  private static final int INPUT_IMAGE_INDEX = 2;
  private final OutputHandler<HandsResult> graphOutputHandler;

  /**
   * Initializes MediaPipe Hands solution.
   *
   * @param context an Android {@link Context}.
   * @param options the configuration options defined in {@link HandsOptions}.
   */
  public Hands(Context context, HandsOptions options) {
    graphOutputHandler = new OutputHandler<>();
    graphOutputHandler.setOutputConverter(
        packets -> {
          HandsResult.Builder handsResultBuilder = HandsResult.builder();
          try {
            handsResultBuilder.setMultiHandLandmarks(
                getProtoVector(packets.get(LANDMARKS_INDEX), NormalizedLandmarkList.parser()));
          } catch (MediaPipeException e) {
            throwException("Error occurs when getting MediaPipe hand landmarks. ", e);
          }
          try {
            handsResultBuilder.setMultiHandedness(
                getProtoVector(packets.get(HANDEDNESS_INDEX), Classification.parser()));
          } catch (MediaPipeException e) {
            throwException("Error occurs when getting MediaPipe handedness data. ", e);
          }
          return handsResultBuilder
              .setImagePacket(packets.get(INPUT_IMAGE_INDEX))
              .setTimestamp(
                  staticImageMode ? Long.MIN_VALUE : packets.get(INPUT_IMAGE_INDEX).getTimestamp())
              .build();
        });

    SolutionInfo solutionInfo =
        SolutionInfo.builder()
            .setBinaryGraphPath(SOLUTION_GRAPH_NAME)
            .setImageInputStreamName(IMAGE_INPUT_STREAM)
            .setOutputStreamNames(OUTPUT_STREAMS)
            .setStaticImageMode(options.mode() == HandsOptions.STATIC_IMAGE_MODE)
            .build();

    initialize(context, solutionInfo, graphOutputHandler);
    Map<String, Packet> inputSidePackets = new HashMap<>();
    inputSidePackets.put(NUM_HANDS, packetCreator.createInt32(options.maxNumHands()));
    start(inputSidePackets);
  }

  /**
   * Sets a callback to be invoked when the HandsResults become available.
   *
   * @param listener the {@link ResultListener} callback.
   */
  public void setResultListener(ResultListener<HandsResult> listener) {
    this.graphOutputHandler.setResultListener(listener);
  }

  /**
   * Sets a callback to be invoked when the Hands solution throws errors.
   *
   * @param listener the {@link ErrorListener} callback.
   */
  public void setErrorListener(@Nullable ErrorListener listener) {
    this.graphOutputHandler.setErrorListener(listener);
    this.errorListener = listener;
  }

  /**
   * Gets a specific hand landmark by hand index and hand landmark type.
   *
   * @param result the returned {@link HandsResult} object.
   * @param handIndex the hand index. The hand landmark lists are sorted by the confidence score.
   * @param landmarkType the hand landmark type defined in {@link HandLandmark}.
   */
  public static NormalizedLandmark getHandLandmark(
      HandsResult result, int handIndex, @HandLandmark.HandLandmarkType int landmarkType) {
    if (result == null
        || handIndex >= result.multiHandLandmarks().size()
        || landmarkType >= HandLandmark.NUM_LANDMARKS) {
      return NormalizedLandmark.getDefaultInstance();
    }
    return result.multiHandLandmarks().get(handIndex).getLandmarkList().get(landmarkType);
  }
}
