/* kmeans.h */
#include <string>
#include <vector>
#include <stdio.h>
#include <stdlib.h>

class Cluster;
class Point;

class KMeans {
  
  static const int maxIters = 250;
  static const float convergenceThreshold = 0.00001;
  static const int numKMeans = 10;
  static const long seed = 123454321;
  
  int numIters;
  int numClusters;
  int dimensions;
  char *s;
  int numChanges;
  
  int numPoints;
  vector<Cluster*> clusters;
  float *data;
  float *means;
  float *stdev;
    
  vector<Point*> points; 
    
  FILE* DataFp;
  
  Point* getPoint(void);

  void initClusters(void);
  
  void readPoints();
  
  int closestCluster(Point *p);

 public:
  KMeans (char *s);

  ~KMeans ();
  vector<Point*> getPoints(void);
  
  int getNumPoints(void);
  
  int iterate(void);
  
  int getNumChanges(void);
  
  void doCluster(int k);

  void outputPoints (void);

  float sumErrors(void);
  
  void standardizePoints(void);

  float absolute (float);
}; 

  
/* End of file kmeans.h */
