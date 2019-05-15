package controller

import (
	cassandraoperatorv1alpha1 "github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"strconv"
	"strings"
)

func cassandraJVMOptions(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) (*string, error) {
	// TODO: should this be Limits or Requests?
	jvmHeapSize := jvmHeapSize(memoryLimit(cdc))

	var writer strings.Builder

	if err := writeFormattedProperties(&writer, map[string]string{
		"-Xms%s\n": strconv.FormatInt(jvmHeapSize, 10),
		"-Xmx%s\n": strconv.FormatInt(jvmHeapSize, 10),
	}); err != nil {
		return nil, err
	}

	// copied from stock jvm.options
	if !isUsingGC1(jvmHeapSize) {

		// young gen size
		if err := writeFormattedProperty(&writer, "-Xmn%s\n", strconv.FormatInt(youngGen(jvmHeapSize), 10)); err != nil {
			return nil, err
		}

		if err := writePropertiesWithNewLine(&writer, []string{
			"-XX:+UseParNewGC",
			"-XX:+UseConcMarkSweepGC",
			"-XX:+CMSParallelRemarkEnabled",
			"-XX:SurvivorRatio=8",
			"-XX:MaxTenuringThreshold=1",
			"-XX:CMSInitiatingOccupancyFraction=75",
			"-XX:+UseCMSInitiatingOccupancyOnly",
			"-XX:CMSWaitDuration=10000",
			"-XX:+CMSParallelInitialMarkEnabled",
			"-XX:+CMSEdenChunksRecordAlways",
			"-XX:+CMSClassUnloadingEnabled",
		}); err != nil {
			return nil, err
		}
	} else {

		if err := writePropertiesConditionally(&writer, []conditionalProperty{
			{property: "-XX:+UseG1GC",},
			{property: "-XX:G1RSetUpdatingPauseTimePercent=5",},
			{property: "-XX:MaxGCPauseMillis=500",},
			{
				condition: func() bool {
					return jvmHeapSize > 16*GIBIBYTE
				},
				property: "-XX:InitiatingHeapOccupancyPercent=70",
			},
		}); err != nil {
			return nil, err
		}

		// TODO: tune -XX:ParallelGCThreads, -XX:ConcGCThreads
	}

	// OOM Error handling
	if err := writePropertiesWithNewLine(&writer, []string{
		"-XX:+HeapDumpOnOutOfMemoryError",
		"-XX:+CrashOnOutOfMemoryError",
	}); err != nil {
		return nil, err
	}

	// TODO: maybe tune -Dcassandra.available_processors=number_of_processors - Wait till we build C* for Java 11
	// not sure if k8s exposes the right number of CPU cores inside the container

	result := writer.String()

	return &result, nil
}

func memoryLimit(cdc *cassandraoperatorv1alpha1.CassandraDataCenter) int64 {
	return cdc.Spec.Resources.Limits.Memory().Value()
}

func jvmHeapSize(memoryLimit int64) int64 {
	return maxInt64(
		minInt64(memoryLimit/2, GIBIBYTE),
		minInt64(memoryLimit/4, 8*GIBIBYTE))
}

func youngGen(jvmHeapSize int64) int64 {

	coreCount := int64(4) // TODO

	return minInt64(coreCount*MEBIBYTE, jvmHeapSize/4)
}

func isUsingGC1(jvmHeapSize int64) bool {
	return jvmHeapSize > 8*GIBIBYTE
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
