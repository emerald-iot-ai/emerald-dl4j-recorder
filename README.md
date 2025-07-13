# Emerald DeepLearning4J Data Recorder
A Java application to receive and record sensor data from Emerald Termux.

## Prerequisites
1. Have Java 21 or higher installed on your local machine
2. Have a current Maven installed on your local machine
3. Make sure TCP port 5000 is not being used by another server on your local machine

## How to run Emerald DeepLearning4J Data Recorder
After cloning this repository open a shell (e.g. cmd on Windows) go to the directory you cloned this repository into and run `mvn clean install exec:java`. This builds and starts the app. Once the app is started run [Emerald Termux](https://github.com/emerald-iot-ai/emerald-termux) to connect to it and start/stop recording your sensor data samples by pushing the respective buttons.

## Where to find your samples
After you've recorded at least one sample you'll find all recorded samples within the `./recorded-data` folder as enumerated .csv files. The sample is always recorded in a data_<sample index>.csv file, while the corresponding label is recorded in the label_<sample index>.csv file where both sample indexes are the same number.

That's it!

**Happy recording! :-)**
