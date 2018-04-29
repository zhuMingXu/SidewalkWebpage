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
import re
from sklearn.cluster import KMeans
from concurrent.futures import ProcessPoolExecutor, ThreadPoolExecutor

# Custom distance function that returns max float if from the same turker id, haversine distance otherwise
def custom_dist(u, v):
    if u[2] == v[2]:
        return sys.float_info.max
    else:
        return haversine([u[0], u[1]], [v[0], v[1]])

# For each label type, cluster based on distance
def cluster(labels, curr_type, thresholds, single_user):

    if single_user:
        dist_matrix = pdist(np.array(labels[['lat', 'lng']].as_matrix()), lambda x, y: haversine(x, y))
    else:
        dist_matrix = pdist(np.array(labels[['lat', 'lng', 'turker_id']].as_matrix()), custom_dist)
    link = linkage(dist_matrix, method='complete')

    # Cuts tree so that only labels less than clust_threth kilometers apart are clustered, adds a col
    # to dataframe with label for the cluster they are in
    labelsCopy = labels.copy()
    labelsCopy.loc[:,'cluster'] = fcluster(link, t=thresholds[curr_type], criterion='distance')
    newClustId = np.max(labelsCopy.cluster) + 1

    # Majority vote to decide what is included. If a cluster has at least MAJORITY_VOTE people agreeing on the type
    # of the label, it is included.
    included_labels = [] # list of tuples (label_type, cluster_num, lat, lng, severity, temporary)
    problem_label_indices = [] # list of indices in dataset of labels that were not agreed upon (not currently used)
    clusters = labelsCopy.groupby('cluster')
    agreement_count = 0
    disagreement_count = 0
    for clust_num, clust in clusters:

        # do majority vote
        # if len(clust) >= MAJORITY_THRESHOLD:
        ave = np.mean(clust['coords'].tolist(), axis=0) # use ave pos of clusters
        ave_sev = None if pd.isnull(clust['severity']).all() else int(round(np.nanmedian(clust['severity'])))
        ave_temp = None if pd.isnull(clust['temporary']).all() else bool(1 - round(1 - np.mean(clust['temporary'])))
        included_labels.append((curr_type, clust_num, ave[0], ave[1], ave_sev, ave_temp))
        # agreement_count += 1
        # else:
        #     problem_label_indices.extend(clust.index)
        #     disagreement_count += 1

    included = pd.DataFrame(included_labels, columns=['label_type', 'cluster', 'lat', 'lng', 'severity', 'temporary'])

    if DEBUG:
        print 'We agreed on this many ' + curr_type + ' labels: ' + str(agreement_count)
        print 'We disagreed on this many ' + curr_type + ' labels: ' + str(disagreement_count)

    return (included, labelsCopy, curr_type, agreement_count, disagreement_count)


if __name__ == '__main__':

    POST_HEADER = {'content-type': 'application/json; charset=utf-8'}

    # Read in arguments from command line
    parser = argparse.ArgumentParser(description='Takes a set of labels from JSON, and outputs the labels grouped into clusters as JSON')
    parser.add_argument('--route_id', type=int,
                        help='Route Id who\'s labels should be clustered.')
    parser.add_argument('--hit_id', type=str,
                        help='HIT Id who\'s labels should be clustered.')
    parser.add_argument('--n_labelers', type=int, default=1,
                        help='Number of turkers to cluster.')
    parser.add_argument('--user_id', type=str,
                        help='User id of a single user who\'s labels should be clustered.')
    parser.add_argument('--region_id', type=int,
                        help='Region id of a region who\'s user-clustered should be clustered.')
    parser.add_argument('--turker_id', type=str,
                        help='Turker id of a single turker who\'s labels should be clustered.')
    parser.add_argument('--is_registered', action='store_true',
                        help='Denotes that the user is registered.')
    parser.add_argument('--clust_thresh', type=float, default=0.0075,
                        help='Cluster distance threshold (in meters)')
    parser.add_argument('--debug', action='store_true',
                        help='Debug mode adds print statements')
    parser.add_argument('--old', action='store_true',
                        help='Indicates that we are doing the version of multi-turker clustering for accuracy.')
    parser.add_argument('--session_ids', nargs='+', type=int,
                        help='List of clustering session ids from which to get labels for clustering')
    parser.add_argument('--worker_type', type=str,
                        help='One of either \'volunteer\' or \'turker\'.')
    args = parser.parse_args()
    DEBUG = args.debug
    OLD = args.old
    IS_REGISTERED = args.is_registered
    CLUSTER_THRESHOLD = args.clust_thresh
    ROUTE_ID = args.route_id
    HIT_ID = args.hit_id
    N_LABELERS = args.n_labelers
    USER_ID = args.user_id.strip('\'\"') if args.user_id else args.user_id
    REGION_ID = args.region_id
    TURKER_ID = args.turker_id
    SINGLE_USER = False
    SESSION_IDS = args.session_ids
    WORKER_TYPE = args.worker_type


    # Determine what type of clustering should be done from command line args, and set variable accordingly.
    MAJORITY_THRESHOLD = None
    getURL = None
    postURL = None
    if SESSION_IDS and OLD:
        getURL = 'http://localhost:9000/clusteredTurkerLabels/' + re.sub('[\[\] ]', '', str(SESSION_IDS))
        if ROUTE_ID: getURL += ('?routeId=' + str(ROUTE_ID))
        postURL = 'http://localhost:9000/clusteringResults' \
                  '/' +str(ROUTE_ID) +\
                  '/' + str(CLUSTER_THRESHOLD) +\
                  '?fromClusters=true'
        MAJORITY_THRESHOLD = math.ceil(N_LABELERS / 2.0)
        SINGLE_USER = False
    elif SESSION_IDS:
        getURL = 'http://localhost:9000/clusteredTurkerLabels/' + re.sub('[\[\] ]', '', str(SESSION_IDS))
        postURL = 'http://localhost:9000/multiUserClusteringResults' \
                  '?threshold=' + str(CLUSTER_THRESHOLD) + \
                  '&forProduction=false'
        if ROUTE_ID:
            getURL += ('?routeId=' + str(ROUTE_ID))
            postURL += ('&routeId=' + str(ROUTE_ID))
        MAJORITY_THRESHOLD = math.ceil(N_LABELERS / 2.0)
        SINGLE_USER = False
    elif ROUTE_ID and TURKER_ID:
        getURL = 'http://localhost:9000/singleTurkerLabels/' + str(TURKER_ID) + '/' + str(ROUTE_ID)
        postURL = 'http://localhost:9000/singleUserClusteringResults' \
                  '?volunteerOrTurkerId=' + str(TURKER_ID) +\
                  '&threshold=' + str(CLUSTER_THRESHOLD) +\
                  '&routeId=' + str(ROUTE_ID)
        SINGLE_USER = True
        MAJORITY_THRESHOLD = 1
    elif ROUTE_ID and USER_ID:
        getURL = 'http://localhost:9000/volunteerLabelsToCluster/' + str(ROUTE_ID)
        postURL = 'http://localhost:9000/singleUserClusteringResults' \
                  '?volunteerOrTurkerId=' + str(USER_ID) + \
                  '&threshold=' + str(CLUSTER_THRESHOLD) + \
                  '&routeId=' + str(ROUTE_ID)
        SINGLE_USER = True
        MAJORITY_THRESHOLD = 1
    elif USER_ID:
        getURL = 'http://localhost:9000/userLabelsToCluster/' + str(USER_ID) + \
                 '?isRegistered=' + str.lower(str(IS_REGISTERED))
        postURL = 'http://localhost:9000/singleUserClusteringResults' + \
                  '?volunteerOrTurkerId=' + str(USER_ID) + \
                  '&threshold=' + str(CLUSTER_THRESHOLD)
        SINGLE_USER = True
        MAJORITY_THRESHOLD = 1
    elif HIT_ID: # this has been used primarily for GT
        MAJORITY_THRESHOLD = 2
        SINGLE_USER = False
        getURL = 'http://localhost:9000/labelsToCluster/' + str(ROUTE_ID) + '/' + str(HIT_ID)
        postURL = 'http://localhost:9000/clusteringResults/' + str(ROUTE_ID) + '/' + str(CLUSTER_THRESHOLD)
    elif REGION_ID:
        SINGLE_USER = False
        getURL = 'http://localhost:9000/clusteredLabelsInRegion?regionId=' + str(REGION_ID)
        postURL = 'http://localhost:9000/multiUserClusteringResults' \
                  '?threshold=' + str(CLUSTER_THRESHOLD) + \
                  '&forProduction=true'
    else: # this is being used for clustering actual (non-researcher) turkers
        MAJORITY_THRESHOLD = math.ceil(N_LABELERS / 2.0)
        SINGLE_USER = False
        getURL = 'http://localhost:9000/nonGTLabelsToCluster/' + str(ROUTE_ID) + '/' + str(N_LABELERS)
        postURL = 'http://localhost:9000/clusteringResults/' + str(ROUTE_ID) + '/' + str(CLUSTER_THRESHOLD)

    # Send GET request to get labels to be clustered.
    try:
        print getURL
        print postURL
        response = requests.get(getURL)
        data = response.json()
        label_data = json_normalize(data[0])
        # print label_data
    except:
        print "Failed to get labels needed to cluster."
        sys.exit()


    # Define thresholds for individual person clustering (numbers are in kilometers)
    if SINGLE_USER:
        thresholds = {'CurbRamp': 0.002,
                      'NoCurbRamp': 0.002,
                      'SurfaceProblem': 0.0075,
                      'Obstacle': 0.0075,
                      'NoSidewalk': 0.0075,
                      'Occlusion': 0.0075,
                      'Other': 0.0075,
                      'Problem': 0.0075}
    else:
        thresholds = {'CurbRamp': 0.0075,
                      'NoCurbRamp': 0.0075,
                      'SurfaceProblem': 0.01,
                      'Obstacle': 0.01,
                      'NoSidewalk': 0.01,
                      'Occlusion': 0.01,
                      'Other': 0.01,
                      'Problem': 0.01}

    # Check if there are 0 labels. If so, just send the post request and exit.
    if len(label_data) == 0:
        if SINGLE_USER or REGION_ID or (not OLD and SESSION_IDS):
            response = requests.post(postURL, data=json.dumps({'thresholds': [], 'labels': [], 'clusters': []}), headers=POST_HEADER)
        else:
            response = requests.post(postURL, data=json.dumps({'thresholds': [], 'labels': []}), headers=POST_HEADER)
        sys.exit()

    # Pick which label types should be included in clustering
    included_types = ['CurbRamp', 'NoSidewalk', 'Problem', 'Occlusion', 'SurfaceProblem', 'Obstacle', 'Other', 'NoCurbRamp']
    problem_types = ['SurfaceProblem', 'Obstacle', 'NoCurbRamp'] if SINGLE_USER else ['Problem']
    label_data = label_data[label_data.label_type.isin(included_types)]

    # Remove NAs
    # label_data.dropna(inplace=True)

    # Remove weird entries with longitude values (on the order of 10^14)
    if sum(label_data.lng > 360) > 0:
        print 'There are %d invalid longitude vals, removing those entries.' % sum(label_data.lng > 360)
        label_data = label_data.drop(label_data[label_data.lng > 360].index)

    if sum(pd.isnull(label_data.lng)) > 0:
        print 'There are %d NaN longitude vals, removing those entries.' % sum(pd.isnull(label_data.lng))
        label_data = label_data.drop(label_data[pd.isnull(label_data.lng)].index)

    # print out some useful info
    if DEBUG:
        print 'labels in dataset: ' + str(len(label_data))
        for label_type in included_types:
            print 'Number of ' + label_type + ' labels: ' + str(sum(label_data.label_type == label_type))

    # Check if there are 0 labels left after removing ones with errors. If so, just send the post request and exit.
    if len(label_data) == 0:
        if SINGLE_USER or REGION_ID or (not OLD and SESSION_IDS):
            response = requests.post(postURL, data=json.dumps({'thresholds': [], 'labels': [], 'clusters': []}), headers=POST_HEADER)
        else:
            response = requests.post(postURL, data=json.dumps({'thresholds': [], 'labels': []}), headers=POST_HEADER)
        sys.exit()

    # Put lat-lng in a tuple so it plays nice w/ haversine function
    label_data['coords'] = label_data.apply(lambda x: (x.lat, x.lng), axis = 1)
    label_data['id'] =  label_data.index.values

    def cluster_label_type_at_index(i):
        clusterz = pd.DataFrame(columns=cluster_cols)
        labelz = pd.DataFrame(columns=label_cols)
        label_type = included_types[i]
        if label_type == 'Problem':
            type_data = label_data[label_data.label_type.isin(problem_types)]
        else:
            type_data = label_data[label_data.label_type == label_type]

        if type_data.shape[0] > 1:
            (clusterz, labelz, ltype, a_cnt, d_cnt) = cluster(type_data, label_type, thresholds, SINGLE_USER)
        elif type_data.shape[0] == 1:
            labelz = type_data.copy()
            labelz.loc[:,'cluster'] = 1
            clusterz = labelz.filter(items=cluster_cols)

        return (label_type, clusterz, labelz)

    def multiprocessing(func, args, workers):
        with ProcessPoolExecutor(max_workers=workers) as executor:
            res = executor.map(func, args)
        return list(res)

    label_cols = ['label_id', 'label_type', 'cluster']
    cluster_cols = ['label_type', 'cluster', 'lat', 'lng', 'severity', 'temporary']
    label_output = pd.DataFrame(columns=label_cols)
    cluster_output = pd.DataFrame(columns=cluster_cols)
    clustOffset = 0

    all_res = multiprocessing(cluster_label_type_at_index, range(0, len(included_types)), 3)

    for i in range(0, len(included_types)):
        (label_type, clusterz, labelz) = all_res[i]
        if not label_output.empty:
            clustOffset = np.max(label_output.cluster)

        cluster_output = cluster_output.append(clusterz)
        cluster_output.loc[cluster_output['label_type'] == label_type, 'cluster'] += clustOffset

        labelz.cluster += clustOffset
        label_output = label_output.append(labelz.filter(items=label_cols))


    for label_type in included_types:
        print str(label_type) + ": " + str(cluster_output[cluster_output.label_type == label_type].cluster.nunique())


    # Convert to JSON
    cluster_json = cluster_output.to_json(orient='records', lines=False)
    label_json = label_output.to_json(orient='records', lines=False)
    threshold_json = pd.DataFrame({'label_type': thresholds.keys(),
                                   'threshold': thresholds.values()}).to_json(orient='records', lines=False)

    # Clustering for a single user has a different POST function than for clustering multiple users.
    if SINGLE_USER or REGION_ID or (not OLD and SESSION_IDS):
        output_json = json.dumps({'thresholds': json.loads(threshold_json),
                                  'labels': json.loads(label_json),
                                  'clusters': json.loads(cluster_json)})
    else:
        output_json = json.dumps({'thresholds': json.loads(threshold_json),
                                  'labels': json.loads(label_json)})
    # print output_json
    print 'chars in json: ' + str(len(output_json))
    response = requests.post(postURL, data=output_json, headers=POST_HEADER)
    # print response

    sys.exit()
