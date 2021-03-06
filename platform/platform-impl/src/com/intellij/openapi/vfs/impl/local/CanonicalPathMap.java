/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.Pair.pair;

class CanonicalPathMap {
  private static final Logger LOG = Logger.getInstance(FileWatcher.class);

  private final List<String> myRecursiveWatchRoots;
  private final List<String> myFlatWatchRoots;
  private final List<String> myCanonicalRecursiveWatchRoots;
  private final List<String> myCanonicalFlatWatchRoots;
  private final List<Pair<String, String>> myMapping;

  public CanonicalPathMap() {
    myRecursiveWatchRoots = myCanonicalRecursiveWatchRoots = myFlatWatchRoots = myCanonicalFlatWatchRoots = Collections.emptyList();
    myMapping = Collections.emptyList();
  }

  public CanonicalPathMap(@NotNull List<String> recursive, @NotNull List<String> flat) {
    myRecursiveWatchRoots = ContainerUtil.newArrayList(recursive);
    myFlatWatchRoots = ContainerUtil.newArrayList(flat);

    List<Pair<String, String>> mapping = ContainerUtil.newSmartList();
    myCanonicalRecursiveWatchRoots = mapPaths(recursive, mapping);
    myCanonicalFlatWatchRoots = mapPaths(flat, mapping);
    myMapping = ContainerUtil.createLockFreeCopyOnWriteList(mapping);
  }

  private static List<String> mapPaths(List<String> paths, List<Pair<String, String>> mapping) {
    List<String> canonicalPaths = ContainerUtil.newArrayList(paths);
    for (int i = 0; i < paths.size(); i++) {
      String path = paths.get(i);
      String canonicalPath = FileSystemUtil.resolveSymLink(path);
      if (canonicalPath != null && !path.equals(canonicalPath)) {
        canonicalPaths.set(i, canonicalPath);
        mapping.add(pair(canonicalPath, path));
      }
    }
    return canonicalPaths;
  }

  public List<String> getCanonicalRecursiveWatchRoots() {
    return myCanonicalRecursiveWatchRoots;
  }

  public List<String> getCanonicalFlatWatchRoots() {
    return myCanonicalFlatWatchRoots;
  }

  public void addMapping(@NotNull Collection<Pair<String, String>> mapping) {
    myMapping.addAll(mapping);
  }

  /**
   * Maps reported paths from canonical representation to requested paths, then filters out those which do not fall under watched roots.
   *
   * <h3>Exactness</h3>
   * Some watchers (notable the native one on OS X) report a parent directory as dirty instead of the "exact" file path.
   * <p>
   * For flat roots, it means that if and only if the exact dirty file path is returned, we should compare the parent to the flat roots,
   * otherwise we should compare to path given to us because it is already the parent of the actual dirty path.
   * <p>
   * For recursive roots, if the path given to us is already the parent of the actual dirty path, we need to compare the path to the parent
   * of the recursive root because if the root itself was changed, we need to know about it.
   */
  @NotNull
  public Collection<String> getWatchedPaths(@NotNull String reportedPath, boolean isExact, boolean fastPath) {
    if (myFlatWatchRoots.isEmpty() && myRecursiveWatchRoots.isEmpty()) return Collections.emptyList();

    Collection<String> affectedPaths = applyMapping(reportedPath);
    Collection<String> changedPaths = ContainerUtil.newSmartList();

    ext:
    for (String path : affectedPaths) {
      if (fastPath && !changedPaths.isEmpty()) break;

      for (String root : myFlatWatchRoots) {
        if (FileUtil.namesEqual(path, root)) {
          changedPaths.add(path);
          continue ext;
        }
        if (isExact) {
          String parentPath = new File(path).getParent();
          if (parentPath != null && FileUtil.namesEqual(parentPath, root)) {
            changedPaths.add(path);
            continue ext;
          }
        }
      }

      for (String root : myRecursiveWatchRoots) {
        if (FileUtil.startsWith(path, root)) {
          changedPaths.add(path);
          continue ext;
        }
        if (!isExact) {
          String parentPath = new File(root).getParent();
          if (parentPath != null && FileUtil.namesEqual(path, parentPath)) {
            changedPaths.add(root);
            continue ext;
          }
        }
      }
    }

    if (!fastPath && changedPaths.isEmpty() && LOG.isDebugEnabled()) {
      LOG.debug("Not watchable, filtered: " + reportedPath);
    }

    return changedPaths;
  }

  private Collection<String> applyMapping(String reportedPath) {
    Collection<String> affectedPaths = ContainerUtil.newSmartList(reportedPath);
    for (Pair<String, String> map : myMapping) {
      if (FileUtil.startsWith(reportedPath, map.first)) {
        affectedPaths.add(map.second + reportedPath.substring(map.first.length()));
      }
      else if (FileUtil.startsWith(reportedPath, map.second)) {
        affectedPaths.add(map.first + reportedPath.substring(map.second.length()));
      }
    }
    return affectedPaths;
  }
}