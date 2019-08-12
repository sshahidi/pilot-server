# pilot-server

An old repository for a pilot-server that was used for benchmarking the scalability of localization algorithms.

Here's the paper in which we have published the results: https://ieeexplore.ieee.org/abstract/document/8292265

## Details about the server:

The server uses received signal strenth (RSS) indicator of wifi accesspoints to locate the user, via applying the K-nearest neigbours algorithm on a databse of previously stored RSS values tagged with their GPS coordinates (more info about the algo, or the performance optimizations in the paper).
