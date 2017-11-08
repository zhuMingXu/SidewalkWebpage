import requests
import math
import numpy as np
from haversine import haversine # pip install haversine
import sys
from scipy.cluster.hierarchy import linkage, fcluster, dendrogram
from scipy.spatial.distance import pdist
from collections import Counter
# from requests.auth import HTTPDigestAuth
import pandas as pd
from pandas.io.json import json_normalize
import json
import argparse
from sklearn.cluster import KMeans

# Custom distance function that returns max float if from the same turker id, haversine distance otherwise
def custom_dist(u, v):
    if u[2] == v[2]:
        return sys.float_info.max
    else:
        return haversine([u[0], u[1]], [v[0], v[1]])

# for each label type, cluster based on distance
def cluster(labels, clust_thresh, single_user):
    if single_user:
        dist_matrix = pdist(np.array(labels[['lat', 'lng']].as_matrix()), lambda x, y: haversine(x, y))
    else:
        dist_matrix = pdist(np.array(labels[['lat', 'lng', 'turker_id']].as_matrix()), custom_dist)
    link = linkage(dist_matrix, method='complete')
    curr_type = labels.label_type.iloc[1]

    # cuts tree so that only labels less than clust_threth kilometers apart are clustered, adds a col
    # to dataframe with label for the cluster they are in
    labels.loc[:,'cluster'] = fcluster(link, t=clust_thresh, criterion='distance')
    labelsCopy = labels.copy()
    newClustId = np.max(labels.cluster) + 1
    #print pd.DataFrame(pd.DataFrame(labels.groupby('cluster').size().rename('points_count')).groupby('points_count').size().rename('points_count_frequency'))

    # Majority vote to decide what is included. If a cluster has at least 3 people agreeing on the type
    # of the label, that is included. Any less, and we add it to the list of problem_clusters, so that
    # we can look at them by hand through the admin interface to decide.
    included_labels = [] # list of tuples (label_type, cluster_num, lat, lng, severity, temporary)
    problem_label_indices = [] # list of indices in dataset of labels that need to be verified
    clusters = labelsCopy.groupby('cluster')
    agreement_count = 0
    disagreement_count = 0
    for clust_num, clust in clusters:

        # do majority vote
        if len(clust) >= MAJORITY_THRESHOLD:
            ave = np.mean(clust['coords'].tolist(), axis=0) # use ave pos of clusters
            ave_sev = None if pd.isnull(clust['severity']).all() else int(round(np.nanmedian(clust['severity'])))
            ave_temp = None if pd.isnull(clust['temporary']).all() else bool(1 - round(1 - np.mean(clust['temporary'])))
            included_labels.append((curr_type, clust_num, ave[0], ave[1], ave_sev, ave_temp))
            agreement_count += 1
        else:
            problem_label_indices.extend(clust.index)
            disagreement_count += 1

    included = pd.DataFrame(included_labels, columns=['label_type', 'cluster', 'lat', 'lng', 'severity', 'temporary'])

    if DEBUG:
        print 'We agreed on this many ' + curr_type + ' labels: ' + str(agreement_count)
        print 'We disagreed on this many ' + curr_type + ' labels: ' + str(disagreement_count)

    return (included, curr_type, clust_thresh, agreement_count, disagreement_count)


if __name__ == '__main__':

    MAJORITY_THRESHOLD = 3 # NOTE: This variable doesn't have any real effect, only used to print summary stats.

    # read in arguments from command line
    parser = argparse.ArgumentParser(description='Takes a set of labels from JSON, and outputs the labels grouped into clusters as JSON')
    parser.add_argument('route_id', type=int,
                        help='Route Id who\'s labels should be clustered.')
    parser.add_argument('--hit_id', type=str,
                        help='HIT Id who\'s labels should be clustered.')
    parser.add_argument('--n_labelers', type=int, default=1,
                        help='Number of turkers to cluster.')
    parser.add_argument('--user_id', type=str,
                        help='User id of a single user who\'s labels should be clustered.')
    parser.add_argument('--clust_thresh', type=float, default=0.0075,
                        help='Cluster distance threshold (in meters)')
    parser.add_argument('--debug', action='store_true',
                        help='Debug mode adds print statements')
    args = parser.parse_args()
    DEBUG = args.debug
    CLUSTER_THRESHOLD = args.clust_thresh
    ROUTE_ID = args.route_id
    HIT_ID = args.hit_id
    N_LABELERS = args.n_labelers
    USER_ID = args.user_id
    SINGLE_USER = False


    try:
        url = None
        if USER_ID:
            url = "http://localhost:9000/userLabelsToCluster/" + str(USER_ID)
            SINGLE_USER = True
            MAJORITY_THRESHOLD = 1
        elif HIT_ID: # this has been used primarily for GT
            MAJORITY_THRESHOLD = 2
            url = 'http://localhost:9000/labelsToCluster/' + str(ROUTE_ID) + '/' + str(HIT_ID)
        else: # this is being used for clustering actual (non-researcher) turkers
            MAJORITY_THRESHOLD = math.ceil(N_LABELERS / 2.0)
            url = 'http://localhost:9000/nonGTLabelsToCluster/' + str(ROUTE_ID) + '/' + str(N_LABELERS)
        print url
        response = requests.get(url)
        data = response.json()
        label_data = json_normalize(data[0])

    except:
        print "bleep bloop fail"
        sys.exit()


    # remove other, occlusion, and no sidewalk label types
    included_types = ['CurbRamp', 'SurfaceProblem', 'Obstacle', 'NoCurbRamp', 'NoSidewalk', 'Occlusion', 'Other']
    label_data = label_data[label_data.label_type.isin(included_types)]

    # remove NAs
    # label_data.dropna(inplace=True)

    # remove weird entries with longitude values (on the order of 10^14)
    if sum(label_data.lng > 360) > 0:
        print 'There are %d invalid longitude vals, removing those entries.' % sum(label_data.lng > 360)
        label_data = label_data.drop(label_data[label_data.lng > 360].index)

    # print out some useful info
    if DEBUG:
        print 'labels in dataset: ' + str(len(label_data))
        for label_type in included_types:
            print 'Number of ' + label_type + ' labels: ' + str(sum(label_data.label_type == label_type))

    # Put lat-lng in a tuple so it plays nice w/ haversine function
    label_data['coords'] = label_data.apply(lambda x: (x.lat, x.lng), axis = 1)
    label_data['id'] =  label_data.index.values

    # Cluster labels for each datatype, and add the results to output_data
    label_cols = ['label_id', 'label_type', 'cluster']
    cluster_cols = ['label_type', 'cluster', 'lat', 'lng', 'severity', 'temporary']
    label_output = pd.DataFrame(columns=label_cols)
    cluster_output = pd.DataFrame(columns=cluster_cols)
    clustOffset = 0
    for label_type in included_types:
        if not label_output.empty:
            clustOffset = np.max(label_output.cluster)

        type_data = label_data[label_data.label_type == label_type]
        if type_data.shape[0] > 1:
            cluster_output = cluster_output.append(cluster(type_data, CLUSTER_THRESHOLD, SINGLE_USER)[0])
            cluster_output.loc[cluster_output['label_type'] == label_type, 'cluster'] += clustOffset

            label_output = label_output.append(type_data.filter(items=label_cols))
            label_output.loc[label_output['label_type'] == label_type, 'cluster'] += clustOffset
        elif type_data.shape[0] == 1:
            type_data.loc[:,'cluster'] = 1 + clustOffset
            label_output = label_output.append(type_data.filter(items=label_cols))
            cluster_output = cluster_output.append(type_data.filter(items=cluster_cols))

    # Convert to JSON
    cluster_json = cluster_output.to_json(orient='records', lines=False)
    label_json = label_output.to_json(orient='records', lines=False)
    output_json = json.dumps({'labels': json.loads(label_json), 'clusters': json.loads(cluster_json)})
    # print output_json

    url = ''
    if SINGLE_USER:
        url = 'http://localhost:9000/singlePersonClusteringResults/' + str(USER_ID) + '/' + str(CLUSTER_THRESHOLD)
    else:
        url = 'http://localhost:9000/clusteringResults/' + str(ROUTE_ID) + '/' + str(CLUSTER_THRESHOLD)
    headers = {'content-type': 'application/json; charset=utf-8'}

    response = requests.post(url, data=output_json, headers=headers)

    sys.exit()
