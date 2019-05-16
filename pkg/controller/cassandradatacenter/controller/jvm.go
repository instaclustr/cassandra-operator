package controller

import (
	"fmt"
	"strings"
)

const (
	MEBIBYTE = 1 << 20
	GIBIBYTE = 1 << 30
)

func addCassandraJVMOptions(config configurationResources) {

	// TODO: should this be Limits or Requests?
	memoryLimit := config.cdc.Spec.Resources.Limits.Memory().Value()

	jvmHeapSize := maxInt64(minInt64(memoryLimit/2, GIBIBYTE), minInt64(memoryLimit/4, 8*GIBIBYTE))

	youngGenSize := youngGen(jvmHeapSize)

	useG1GC := jvmHeapSize > 8*GIBIBYTE

	var writer strings.Builder

	_, _ = fmt.Fprintf(&writer, "-Xms%d\n", jvmHeapSize) // min heap size
	_, _ = fmt.Fprintf(&writer, "-Xmx%d\n", jvmHeapSize) // max heap size

	// copied from stock jvm.options
	if !useG1GC {
		_, _ = fmt.Fprintf(&writer, "-Xmn%d\n", youngGenSize) // young gen size

		_, _ = fmt.Fprintln(&writer, "-XX:+UseParNewGC")
		_, _ = fmt.Fprintln(&writer, "-XX:+UseConcMarkSweepGC")
		_, _ = fmt.Fprintln(&writer, "-XX:+CMSParallelRemarkEnabled")
		_, _ = fmt.Fprintln(&writer, "-XX:SurvivorRatio=8")
		_, _ = fmt.Fprintln(&writer, "-XX:MaxTenuringThreshold=1")
		_, _ = fmt.Fprintln(&writer, "-XX:CMSInitiatingOccupancyFraction=75")
		_, _ = fmt.Fprintln(&writer, "-XX:+UseCMSInitiatingOccupancyOnly")
		_, _ = fmt.Fprintln(&writer, "-XX:CMSWaitDuration=10000")
		_, _ = fmt.Fprintln(&writer, "-XX:+CMSParallelInitialMarkEnabled")
		_, _ = fmt.Fprintln(&writer, "-XX:+CMSEdenChunksRecordAlways")
		_, _ = fmt.Fprintln(&writer, "-XX:+CMSClassUnloadingEnabled")

	} else {
		_, _ = fmt.Fprintln(&writer, "-XX:+UseG1GC")
		_, _ = fmt.Fprintln(&writer, "-XX:G1RSetUpdatingPauseTimePercent=5")
		_, _ = fmt.Fprintln(&writer, "-XX:MaxGCPauseMillis=500")

		if jvmHeapSize > 16*GIBIBYTE {
			_, _ = fmt.Fprintln(&writer, "-XX:InitiatingHeapOccupancyPercent=70")
		}

		// TODO: tune -XX:ParallelGCThreads, -XX:ConcGCThreads
	}

	// OOM Error handling
	_, _ = fmt.Fprintln(&writer, "-XX:+HeapDumpOnOutOfMemoryError")
	_, _ = fmt.Fprintln(&writer, "-XX:+CrashOnOutOfMemoryError")

	// TODO: maybe tune -Dcassandra.available_processors=number_of_processors - Wait till we build C* for Java 11
	// not sure if k8s exposes the right number of CPU cores inside the container

	configMapVolumeAddTextFile(config.configMap, config.volumeSource, ConfigMapJVMMemoryGcOptionsFilePath, writer.String())
}

func youngGen(jvmHeapSize int64) int64 {

	coreCount := int64(4) // TODO

	return minInt64(coreCount*MEBIBYTE, jvmHeapSize/4)
}

func minInt64(a int64, b int64) int64 {
	if a < b {
		return a
	}
	return b
}

func maxInt64(a int64, b int64) int64 {
	if a > b {
		return a
	}
	return b
}
