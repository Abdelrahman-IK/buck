/*
 * Copyright (c) Facebook, Inc. and its affiliates.
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

package com.facebook.buck.rules.coercer;

import com.facebook.buck.core.cell.nameresolver.CellNameResolver;
import com.facebook.buck.core.linkgroup.CxxLinkGroupMappingTarget;
import com.facebook.buck.core.linkgroup.UnconfiguredCxxLinkGroupMappingTarget;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.model.UnconfiguredBuildTarget;
import com.facebook.buck.core.path.ForwardRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.google.common.reflect.TypeToken;
import java.util.Collection;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * {@link TypeCoercer} for {@link CxxLinkGroupMappingTarget}.
 *
 * <p>This {@link TypeCoercer} is used to convert a single link group mapping (e.g., <code>
 * ("//Some:Target", "tree")</code>) to a {@link CxxLinkGroupMappingTarget}.
 */
public class CxxLinkGroupMappingTargetCoercer
    implements TypeCoercer<UnconfiguredCxxLinkGroupMappingTarget, CxxLinkGroupMappingTarget> {
  private final TypeCoercer<UnconfiguredBuildTarget, BuildTarget> buildTargetTypeCoercer;
  private final TypeCoercer<
          CxxLinkGroupMappingTarget.Traversal, CxxLinkGroupMappingTarget.Traversal>
      traversalCoercer;
  private final TypeCoercer<Pattern, Pattern> patternTypeCoercer;

  private static final String LABEL_REGEX_PREFIX = "label:";

  public CxxLinkGroupMappingTargetCoercer(
      TypeCoercer<UnconfiguredBuildTarget, BuildTarget> buildTargetTypeCoercer,
      TypeCoercer<CxxLinkGroupMappingTarget.Traversal, CxxLinkGroupMappingTarget.Traversal>
          traversalCoercer,
      TypeCoercer<Pattern, Pattern> patternTypeCoercer) {
    this.buildTargetTypeCoercer = buildTargetTypeCoercer;
    this.traversalCoercer = traversalCoercer;
    this.patternTypeCoercer = patternTypeCoercer;
  }

  @Override
  public TypeToken<CxxLinkGroupMappingTarget> getOutputType() {
    return TypeToken.of(CxxLinkGroupMappingTarget.class);
  }

  @Override
  public TypeToken<UnconfiguredCxxLinkGroupMappingTarget> getUnconfiguredType() {
    return TypeToken.of(UnconfiguredCxxLinkGroupMappingTarget.class);
  }

  @Override
  public boolean hasElementClass(Class<?>... types) {
    return buildTargetTypeCoercer.hasElementClass(types) || traversalCoercer.hasElementClass(types);
  }

  @Override
  public void traverse(
      CellNameResolver cellRoots, CxxLinkGroupMappingTarget object, Traversal traversal) {
    buildTargetTypeCoercer.traverse(cellRoots, object.getBuildTarget(), traversal);
    traversalCoercer.traverse(cellRoots, object.getTraversal(), traversal);
  }

  @Override
  public UnconfiguredCxxLinkGroupMappingTarget coerceToUnconfigured(
      CellNameResolver cellRoots,
      ProjectFilesystem filesystem,
      ForwardRelativePath pathRelativeToProjectRoot,
      Object object)
      throws CoerceFailedException {

    if (object instanceof Collection<?>) {
      Collection<?> collection = ((Collection<?>) object);
      if (2 <= collection.size() && collection.size() <= 3) {
        Object[] objects = collection.toArray();
        UnconfiguredBuildTarget buildTarget =
            buildTargetTypeCoercer.coerceToUnconfigured(
                cellRoots, filesystem, pathRelativeToProjectRoot, objects[0]);
        CxxLinkGroupMappingTarget.Traversal traversal =
            traversalCoercer.coerceToUnconfigured(
                cellRoots, filesystem, pathRelativeToProjectRoot, objects[1]);
        Optional<Pattern> labelPattern = Optional.empty();
        if (collection.size() >= 3) {
          String regexString = extractLabelRegexString(objects[2]);
          labelPattern =
              Optional.of(
                  patternTypeCoercer.coerceToUnconfigured(
                      cellRoots, filesystem, pathRelativeToProjectRoot, regexString));
        }

        return UnconfiguredCxxLinkGroupMappingTarget.of(buildTarget, traversal, labelPattern);
      }
    }

    throw CoerceFailedException.simple(
        object,
        getOutputType(),
        "input should be pair of a build target and traversal, optionally with a label filter");
  }

  private String extractLabelRegexString(Object object) throws CoerceFailedException {
    if (!(object instanceof String)) {
      throw CoerceFailedException.simple(
          object, getOutputType(), "Third element should be a label regex filter");
    }

    String prefixWithRegex = (String) object;
    if (!prefixWithRegex.startsWith(LABEL_REGEX_PREFIX)) {
      throw CoerceFailedException.simple(
          object, getOutputType(), "Label regex filter should start with " + LABEL_REGEX_PREFIX);
    }

    return prefixWithRegex.substring(LABEL_REGEX_PREFIX.length());
  }

  @Override
  public CxxLinkGroupMappingTarget coerce(
      CellNameResolver cellRoots,
      ProjectFilesystem filesystem,
      ForwardRelativePath pathRelativeToProjectRoot,
      TargetConfiguration targetConfiguration,
      TargetConfiguration hostConfiguration,
      UnconfiguredCxxLinkGroupMappingTarget object)
      throws CoerceFailedException {
    return CxxLinkGroupMappingTarget.of(
        object.getBuildTarget().configure(targetConfiguration),
        object.getTraversal(),
        object.getLabelPattern());
  }
}
