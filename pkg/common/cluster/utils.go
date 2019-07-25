package cluster

import "fmt"

func BuildRacksDistribution(nodes, racks int32) map[string]int32 {
	racksDistribution := make(map[string]int32)
	var i int32
	for ; i < racks; i++ {
		rack := fmt.Sprintf("rack%v", i+1)
		replicas := nodes / racks
		if i < (nodes % racks) {
			replicas = replicas + 1
		}
		racksDistribution[rack] = replicas
		fmt.Printf("Rack %v, nodes %v\n", rack, replicas)
	}

	return racksDistribution
}
