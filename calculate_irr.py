import pandas as pd
import numpy as np
import sys
from shapely.geometry import MultiLineString, LineString, Point
from shapely import ops
from shapely import wkb
from geoalchemy2.shape import to_shape
from sqlalchemy import create_engine
from math import floor, ceil
# from haversine import haversine


# for javascript conversion...
#####
# splitting line into segments of specified length: lineChunk -- http://turfjs.org/docs/#linechunk

# "snapping" points to lines (given a point and a line, get closest point on the line to the point)
# http://turfjs.org/docs/#pointonline
# This gives the point that is closest, which line segment in the multilinestring is closest, AND
# the distance from the start of the multilinestring to this point on it


# 3857 epsg
# 4326
# 26918
#http://dfreelon.org/recal/recal-oir.php
for route_hit_pair in [(145, 'hit72'), (146, 'hit72'), (147, 'hit72'), (259, 'hit128'), (260, 'hit128')]:
	route = route_hit_pair[0]
	hit = route_hit_pair[1]

	linestring_query = """
	SELECT   "sidewalk"."street_edge"."geom",
	         "sidewalk"."route_street"."current_street_edge_id"
	FROM     "sidewalk"."route_street" 
	INNER JOIN "sidewalk"."street_edge"  ON "route_street"."current_street_edge_id" = "street_edge"."street_edge_id" 
	INNER JOIN "sidewalk"."amt_volunteer_route"  ON "amt_volunteer_route"."route_id" = "route_street"."route_id" 
	WHERE    ( "sidewalk"."amt_volunteer_route"."route_id" = {0} )
	ORDER BY "sidewalk"."route_street"."route_street_id"
	""".format(route)

	label_query = """
	SELECT   "sidewalk"."amt_assignment"."hit_id",
	         "sidewalk"."amt_assignment"."turker_id",
	         "sidewalk"."amt_assignment"."route_id",
	         "sidewalk"."label"."label_id",
	         "sidewalk"."label_type"."label_type",
	         "sidewalk"."problem_severity"."severity",
	         "sidewalk"."problem_temporariness"."temporary_problem",
	         "sidewalk"."label_point"."lat",
	         "sidewalk"."label_point"."lng"
	FROM     "audit_task" 
	INNER JOIN "amt_assignment"  ON "audit_task"."amt_assignment_id" = "amt_assignment"."amt_assignment_id" 
	INNER JOIN "label"  ON "label"."audit_task_id" = "audit_task"."audit_task_id" 
	INNER JOIN "label_type"  ON "label"."label_type_id" = "label_type"."label_type_id" 
	LEFT OUTER JOIN "problem_severity"  ON "problem_severity"."label_id" = "label"."label_id" 
	LEFT OUTER JOIN "problem_temporariness"  ON "problem_temporariness"."label_id" = "label"."label_id" 
	INNER JOIN "label_point"  ON "label"."label_id" = "label_point"."label_id" 
	WHERE    ( "sidewalk"."amt_assignment"."hit_id" = '{1}' )
				AND ( "sidewalk"."amt_assignment"."route_id" = {0} )
				AND ( "sidewalk"."label"."deleted" = false )
				AND ( "sidewalk"."label"."gsv_panorama_id" <> 'stxXyCKAbd73DmkM2vsIHA')
				AND ( "sidewalk"."amt_assignment"."completed" = true)
	""".format(route, hit)

	engine = create_engine('postgresql://sidewalk:sidewalk@localhost:5432/turk-8-21-17')

	# read in data
	# names = ['geom', 'current_street_edge_id']
	# linestring_data = pd.read_csv('./linestring_data.csv', names=names)
	linestring_data = pd.read_sql(linestring_query, con=engine)
	label_data = pd.read_sql(label_query, con=engine)

	label_data['coords'] = label_data.apply(lambda x: Point(x.lng, x.lat), axis = 1)
	linestring_data.geom = linestring_data.geom.apply(lambda g: wkb.loads(g, hex=True))

	def getIndexOfClosestLine(point, lineSeries):
		lineSeries.apply(lambda line: point.distance(line)).idxmin()


	label_data['closest_line'] = label_data.coords.apply(lambda l: linestring_data.geom.apply(lambda line: l.distance(line)).idxmin())

	route_mlinestring = ops.linemerge(MultiLineString(linestring_data.geom.values.tolist()))

	for seg_dist in [5, 10]:

		label_data['projection'] = label_data.coords.apply(lambda c: route_mlinestring.project(c) * 111 * 1000 / seg_dist)

		# TODO convert to actual haversine distance
		# num_records = int(floor(route_mlinestring.length * 111 * 1000 / 10))
		num_records = int(ceil(label_data.projection.max()))

		cr_out = pd.DataFrame(0, index=range(0,num_records), columns=set(label_data.turker_id.values.tolist()))
		ncr_out = pd.DataFrame(0, index=range(0,num_records), columns=set(label_data.turker_id.values.tolist()))
		sp_out = pd.DataFrame(0, index=range(0,num_records), columns=set(label_data.turker_id.values.tolist()))
		obs_out = pd.DataFrame(0, index=range(0,num_records), columns=set(label_data.turker_id.values.tolist()))
		occ_out = pd.DataFrame(0, index=range(0,num_records), columns=set(label_data.turker_id.values.tolist()))
		oth_out = pd.DataFrame(0, index=range(0,num_records), columns=set(label_data.turker_id.values.tolist()))
		ns_out = pd.DataFrame(0, index=range(0,num_records), columns=set(label_data.turker_id.values.tolist()))

		labeltype_mapping = {
			'CurbRamp': cr_out,
			'NoCurbRamp': ncr_out,
			'SurfaceProblem': sp_out,
			'Obstacle': obs_out,
			'Occlusion': occ_out,
			'Other': oth_out,
			'NoSidewalk': ns_out
		}

		def populateOutput(row):
			index = int(floor(row.projection))
			col = row.turker_id
			old_val = labeltype_mapping[row.label_type].get_value(index, col)
			labeltype_mapping[row.label_type].set_value(index, col, old_val + 1)

		abc = label_data.apply(populateOutput, axis=1)

		all_out = cr_out.append(ncr_out).append(sp_out).append(obs_out).append(occ_out).append(oth_out).append(ns_out)

		with open("irr_results/" + str(seg_dist) + "m-CurbRamp.csv", 'a') as f:
			cr_out.to_csv(f, header=False, index=False)
		with open("irr_results/" + str(seg_dist) + "m-NoCurbRamp.csv", 'a') as f:
			ncr_out.to_csv(f, header=False, index=False)
		with open("irr_results/" + str(seg_dist) + "m-SurfaceProblem.csv", 'a') as f:
			sp_out.to_csv(f, header=False, index=False)
		with open("irr_results/" + str(seg_dist) + "m-Obstacle.csv", 'a') as f:
			obs_out.to_csv(f, header=False, index=False)
		with open("irr_results/" + str(seg_dist) + "m-Occlusion.csv", 'a') as f:
			occ_out.to_csv(f, header=False, index=False)
		with open("irr_results/" + str(seg_dist) + "m-Other.csv", 'a') as f:
			oth_out.to_csv(f, header=False, index=False)
		with open("irr_results/" + str(seg_dist) + "m-NoSidewalk.csv", 'a') as f:
			ns_out.to_csv(f, header=False, index=False)
		with open("irr_results/" + str(seg_dist) + "m-all.csv", 'a') as f:
			all_out.to_csv(f, header=False, index=False)



	num_records = len(linestring_data)

	cr_out = pd.DataFrame(0, index=range(0,num_records), columns=set(label_data.turker_id.values.tolist()))
	ncr_out = pd.DataFrame(0, index=range(0,num_records), columns=set(label_data.turker_id.values.tolist()))
	sp_out = pd.DataFrame(0, index=range(0,num_records), columns=set(label_data.turker_id.values.tolist()))
	obs_out = pd.DataFrame(0, index=range(0,num_records), columns=set(label_data.turker_id.values.tolist()))
	occ_out = pd.DataFrame(0, index=range(0,num_records), columns=set(label_data.turker_id.values.tolist()))
	oth_out = pd.DataFrame(0, index=range(0,num_records), columns=set(label_data.turker_id.values.tolist()))
	ns_out = pd.DataFrame(0, index=range(0,num_records), columns=set(label_data.turker_id.values.tolist()))

	labeltype_mapping = {
		'CurbRamp': cr_out,
		'NoCurbRamp': ncr_out,
		'SurfaceProblem': sp_out,
		'Obstacle': obs_out,
		'Occlusion': occ_out,
		'Other': oth_out,
		'NoSidewalk': ns_out
	}

	def populateStreetOutput(row):
		index = row.closest_line
		col = row.turker_id
		old_val = labeltype_mapping[row.label_type].get_value(index, col)
		labeltype_mapping[row.label_type].set_value(index, col, old_val + 1)

	abc = label_data.apply(populateStreetOutput, axis=1)


	all_out = cr_out.append(ncr_out).append(sp_out).append(obs_out).append(occ_out).append(oth_out).append(ns_out)

	with open("irr_results/street-CurbRamp.csv", 'a') as f:
		cr_out.to_csv(f, header=False, index=False)
	with open("irr_results/street-NoCurbRamp.csv", 'a') as f:
		ncr_out.to_csv(f, header=False, index=False)
	with open("irr_results/street-SurfaceProblem.csv", 'a') as f:
		sp_out.to_csv(f, header=False, index=False)
	with open("irr_results/street-Obstacle.csv", 'a') as f:
		obs_out.to_csv(f, header=False, index=False)
	with open("irr_results/street-Occlusion.csv", 'a') as f:
		occ_out.to_csv(f, header=False, index=False)
	with open("irr_results/street-Other.csv", 'a') as f:
		oth_out.to_csv(f, header=False, index=False)
	with open("irr_results/street-NoSidewalk.csv", 'a') as f:
		ns_out.to_csv(f, header=False, index=False)
	with open("irr_results/street-all.csv", 'a') as f:
		all_out.to_csv(f, header=False, index=False)
