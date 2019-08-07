package cluster

import "fmt"

func BuildRacksDistribution(nodes, racks int32) (racksDistribution Racks) {
	for i := int32(0); i < racks; i++ {
		rack := fmt.Sprintf("rack%v", i+1)
		replicas := nodes / racks
		if i < (nodes % racks) {
			replicas = replicas + 1
		}
		racksDistribution = append(racksDistribution, &Rack{Name: rack, Replicas: replicas})
	}

	return racksDistribution
}

type Rack struct {
	Name     string
	Replicas int32
}

type Racks []*Rack

func (racks Racks) GetRack(rackName string) *Rack {
	for _, r := range racks {
		if r.Name == rackName {
			return r
		}
	}
	return nil
}