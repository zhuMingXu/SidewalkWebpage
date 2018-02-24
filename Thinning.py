#!/usr/bin/python
import psycopg2
import numpy as np


def CreateTables():
    for command in create_tables:
        cur.execute(command)

def DropTables():
    for command in drop_tables:
        cur.execute(command)

def getNumber():
    get_number = [
    """
    select l.label_type_id, count(*)
    from sidewalk.label l, sidewalk.problem_severity ps, sidewalk.label_point p
    where l.label_id = ps.label_id and ps.label_id = p.label_id
    group by l.label_type_id;
    """
    ]

    for command in get_number:
        cur.execute(command)
        rows = cur.fetchall()
        num = np.zeros(7)
        for row in rows:
            num[int(row[0])-1]=int(row[1])
    return num


def getAllPoints():
    get_all_points = []
    for i in range(7):
        get_all_points.append(
    """
select ps.label_id, ps.severity, p.lat, p.lng
from sidewalk.label l, sidewalk.problem_severity ps, sidewalk.label_point p
where l.label_id = ps.label_id and ps.label_id = p.label_id and l.label_type_id = {}
order by ps.severity DESC
    """.format(i+1)
    )
    allPoint = []
    for command in get_all_points:
        cur.execute(command)
        rows = cur.fetchall()
        allPoint.append(np.zeros((len(rows),4)))
        index = 0
        for row in rows:
            allPoint[-1][index] = np.array(row)
            index = index+1
    return allPoint

def calculateZoomLevel():
    zoomLevel = []
    for i in range(LABEL_TYPE):
        for j in range(allPoints[i].shape[0]):
            for z in range(ZOOM_LEVEL):
                if j<=VisNum[z,i]:
                    zoomLevel.append([int(allPoints[i][j][0]),z])
    return zoomLevel

def addZoomLevel():
    for point in zoomLevel[:100]:
        cur.execute("INSERT INTO sidewalk.label_presampled VALUES (%d, %d);"%(point[0],point[1]))

def seperateTables():
    for z in range(ZOOM_LEVEL):
        cur.execute(
        """
        INSERT INTO sidewalk.label_presampled_z%d(
        SELECT ps.label_id
        FROM sidewalk.label_presampled ps
        WHERE ps.zoom_level = %d
        );
        """%(z,z)
        )


def main():
	#make connection
    ZOOM_LEVEL = 7
    LABEL_TYPE = 7
    try:
        conn = psycopg2.connect("dbname='sidewalk' user='sidewalk' host='localhost' port='5433' password='sidewalk'")
    except psycopg2.Error:
        print("I am unable to connect to the database")
    cur = conn.cursor()
    getNumber()
    VisNum = np.zeros((ZOOM_LEVEL,LABEL_TYPE))
    VisNum[ZOOM_LEVEL-1] = getNumber()
    for i in range(ZOOM_LEVEL-1):
        VisNum[ZOOM_LEVEL-2-i] = VisNum[ZOOM_LEVEL-1-i]*0.8
    VisNum.astype(int)
    allPoints = getAllPoints()
    zoomLevel = calculateZoomLevel()
    addZoomLevel()
    seperateTables()


if __name__ == "__main__":
	main()
