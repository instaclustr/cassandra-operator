package cluster

import (
	"github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
	"sort"
)

func BuildRacksDistribution(spec v1alpha1.CassandraDataCenterSpec) (racksDistribution Racks) {

	// Sort racks alphabetically by name
	sort.SliceStable(spec.Racks, func(i, j int) bool {
		return spec.Racks[i].Name < spec.Racks[j].Name
	})


	// Now build the distribution
	numRacks := int32(len(spec.Racks))
	for i, rack := range spec.Racks {
		replicas := spec.Nodes / numRacks
		if i < int(spec.Nodes%numRacks) {
			replicas = replicas + 1
		}
		racksDistribution = append(racksDistribution, &Rack{Name: rack.Name, NodeLabels: rack.Labels, Replicas: replicas})
	}
	return racksDistribution
}

type Rack struct {
	Name       string
	Replicas   int32
	NodeLabels map[string]string
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
