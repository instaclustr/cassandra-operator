package cluster

import (
	"sort"

	"github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
)

func BuildRacksDistribution(cdc *v1alpha1.CassandraDataCenter) (racksDistribution Racks) {

	// If racks are not provided, we place everything in 1 Rack
	// Use cdc Name and Namespace as part of the name to avoid having same
	// rack name for different datacenters
	if cdc.Spec.Racks == nil {
		cdc.Spec.Racks = []v1alpha1.Rack{{Name: cdc.Namespace + "-" + cdc.Name + "-rack"}}
	} else {
		// Sort racks alphabetically by name
		sort.SliceStable(cdc.Spec.Racks, func(i, j int) bool {
			return cdc.Spec.Racks[i].Name < cdc.Spec.Racks[j].Name
		})
	}

	// otherwise, build the distribution
	numRacks := int32(len(cdc.Spec.Racks))
	for i, rack := range cdc.Spec.Racks {
		replicas := cdc.Spec.Nodes / numRacks
		if i < int(cdc.Spec.Nodes%numRacks) {
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
