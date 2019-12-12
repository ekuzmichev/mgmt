package deployment.mgmt.atrifacts.changes;

import deployment.mgmt.atrifacts.Artifact;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.io.File;
import java.util.*;
import java.util.zip.ZipEntry;

import static deployment.mgmt.atrifacts.Artifact.SNAPSHOT;
import static deployment.mgmt.atrifacts.ArtifactType.JAR;
import static deployment.mgmt.atrifacts.strategies.classpathfile.JarClasspathReader.CLASSPATH_FILE;
import static deployment.mgmt.atrifacts.strategies.classpathfile.JarClasspathReader.CLASSPATH_GRADLE_FILE;
import static deployment.mgmt.utils.ZipUtils.containsInnerFile;
import static deployment.mgmt.utils.ZipUtils.forEachInnerFiles;
import static io.microconfig.utils.FileUtils.write;
import static io.microconfig.utils.IoUtils.readFully;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
class ClasspathDiffBuilderImpl implements ClasspathDiffBuilder {
    private final String service;

    private ClasspathIndex previousIndex;
    private ClasspathIndex currentIndex;

    @Override
    public ClasspathDiffBuilder indexPreviousClasspath(List<File> files) {
        previousIndex = ClasspathIndex.hashIndex(files, service, false);
        return this;
    }

    @Override
    public ClasspathDiffBuilder indexCurrentClasspath(List<File> files) {
        currentIndex = ClasspathIndex.hashIndex(files, service, true);
        return this;
    }

    @Override
    public ClasspathDiff compare() {
        List<File> added = currentIndex.getFilesMissingIn(previousIndex);
        List<File> removed = previousIndex.getFilesMissingIn(currentIndex);

        ClasspathIndex addedIndex = ClasspathIndex.artifactNameIndex(added);
        ClasspathIndex removedIndex = ClasspathIndex.artifactNameIndex(removed);

        List<File> changed = addedIndex.removeIntersection(removedIndex);

        return new ClasspathDiff(addedIndex.files(), removedIndex.files(), changed);
    }

    @ToString
    @RequiredArgsConstructor
    private static class ClasspathIndex {
        private final Map<String, List<File>> index;

        public static ClasspathIndex hashIndex(List<File> files, String service, boolean recalcSnapshot) {
            return new ClasspathIndex(files.parallelStream()
                    .filter(File::isFile)
                    .filter(File::exists)
                    .collect(groupingBy(f -> hash(f, service, recalcSnapshot)))
            );
        }

        public static ClasspathIndex artifactNameIndex(List<File> files) {
            return new ClasspathIndex(files.parallelStream().collect(groupingBy(ClasspathIndex::artifactName)));
        }

        public List<File> getFilesMissingIn(ClasspathIndex other) {
            return index.entrySet()
                    .stream()
                    .filter(e -> !other.index.containsKey(e.getKey()))
                    .flatMap(e -> e.getValue().stream())
                    .collect(toList());
        }

        public List<File> removeIntersection(ClasspathIndex other) {
            List<String> keysIntersection = new ArrayList<>(index.keySet());
            keysIntersection.retainAll(other.index.keySet());
            keysIntersection.forEach(other.index::remove);

            return keysIntersection.stream()
                    .map(index::remove)
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .sorted(comparing(File::getPath))
                    .collect(toList());
        }

        public List<File> files() {
            return index.values()
                    .stream()
                    .flatMap(Collection::stream)
                    .sorted(comparing(File::getPath))
                    .collect(toList());
        }

        private static String hash(File file, String currentService, boolean recalcSnapshot) {
            boolean isSnapshot = file.getName().contains(SNAPSHOT);
            String indexName = (isSnapshot ? currentService + "_" : "") + "mgmt.hash";

            File alreadyCalculated = new File(file.getParent(), indexName);
            if (alreadyCalculated.exists() && (!isSnapshot || !recalcSnapshot)) {
                return readFully(alreadyCalculated);
            }

            String hash = doHash(file);
            write(alreadyCalculated, hash);
            return hash;
        }

        private static String doHash(File archive) {
            if (archive.getName().endsWith(JAR.extension()) && containsClasspathFiles(archive)) {
                List<Hasher> hashers = new ArrayList<>();
                forEachInnerFiles(archive, (entry, is) -> {
                    if (!isClasspathFile(entry)) {
                        hashers.add(new Hasher(entry.getName()).hash(is));
                    }
                });

                return Hasher.reduce(hashers);
            }

            return new Hasher("").hash(archive).value();
        }

        private static boolean containsClasspathFiles(File archive) {
            return containsInnerFile(archive, CLASSPATH_FILE) || containsInnerFile(archive, CLASSPATH_GRADLE_FILE);
        }

        private static boolean isClasspathFile(ZipEntry entry) {
            String entryName = entry.getName();
            return entryName.equals(CLASSPATH_FILE) || entryName.equals(CLASSPATH_GRADLE_FILE);
        }

        private static String artifactName(File file) {
            Artifact artifact = Artifact.fromFile(file);
            return artifact.hasUnknownGroupId() ?
                    artifact.getMavenFormatString()
                    : artifact.getGroupId() + ":" + artifact.getArtifactId();
        }
    }
}