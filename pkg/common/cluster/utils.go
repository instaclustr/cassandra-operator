package cluster

import (
	"sort"

	v1 "k8s.io/api/core/v1"

	"github.com/instaclustr/cassandra-operator/pkg/apis/cassandraoperator/v1alpha1"
)

func BuildRacksDistribution(spec v1alpha1.CassandraDataCenterSpec) (racksDistribution Racks) {

	// If racks are not provided, we place everything in 1 Rack
	if spec.Racks == nil {
		spec.Racks = []v1alpha1.Rack{{Name: "rack1"}}
	} else {
		// Sort racks alphabetically by name
		sort.SliceStable(spec.Racks, func(i, j int) bool {
			return spec.Racks[i].Name < spec.Racks[j].Name
		})
	}

	// otherwise, build the distribution
	numRacks := int32(len(spec.Racks))
	for i, rack := range spec.Racks {
		replicas := spec.Nodes / numRacks
		if i < int(spec.Nodes%numRacks) {
			replicas = replicas + 1
		}
		racksDistribution = append(racksDistribution, &Rack{Name: rack.Name, NodeLabels: rack.Labels, Replicas: replicas, Tolerations: rack.Tolerations, Affinity: rack.Affinity})
	}
	return racksDistribution
}

type Rack struct {
	Name        string
	Replicas    int32
	NodeLabels  map[string]string
	Tolerations []v1.Toleration
	Affinity		*v1.Affinity
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
