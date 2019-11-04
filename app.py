import os
import cv2
import h5py
import json
import time
import numpy
import random
import argparse
import progressbar

import IO
import Preprocessor
import Callbacks.EpochCheckpoint as EpochCheckpoint
import Callbacks.TrainingMonitor as TrainingMonitor
import Networks.Convolution.AlexNet.AlexNet as NN

import keras.backend as Backend
from keras.optimizers import Adam
from keras.utils import plot_model
from keras.models import Model
from keras.models import load_model
from keras.callbacks import LearningRateScheduler
from keras.preprocessing.image import ImageDataGenerator
from keras.preprocessing.image import img_to_array

from sklearn.preprocessing import LabelEncoder
from sklearn.model_selection import train_test_split

import matplotlib.pyplot as plt 

import Network

# Define the command line arguments for the application
Parser = argparse.ArgumentParser()
Parser.add_argument("-w", "--write", help = "Generate a new training dataset with images listed in `Download.txt`", required = False, action = "store_true")
Parser.add_argument("-t", "--train", help = "Start the training of the neural network", required = False, action = "store_true")
Parser.add_argument("-p", "--predict", help = "Start the prediction", required = False, action = "store_true")
Parser.add_argument("-o", "--convert", help = "Convert the model into a tensorflow graph", required = False, action = "store_true")
Parser.add_argument("-c", "--checkpoint", help = "Specify the checkpoint directory for training", default = "", required = False)
Parser.add_argument("-s", "--start", help = "Starting checkpoint for training. Only needed with -c option.", type = int, default = 0, required = False)
args = vars(Parser.parse_args())

if(args["write"] == True):
    (R, G, B) = ([], [], [])

    print("[INFO] Step 1: Save data into hdf5 file...")

    # Check if input directory exist
    if(not(os.path.exists(Network.DATASET_PATH))):
        print("[ERROR] Input directory does not exist! Abort...")
        exit()

    # Check if output directory exist
    if(not(os.path.exists(Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH))):
        os.makedirs(Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH)

    # Read the folder for training
    TrainingImageList, TrainingLabelList = IO.FileIO.ReadFiles(Path = Network.DATASET_PATH + os.path.sep + Network.TRAINING)

    # Shuffle the data
    print("[INFO] Shuffle data...")
    ShuffleList = list(zip(TrainingImageList, TrainingLabelList))
    random.shuffle(ShuffleList)
    TrainingImageList, TrainingLabelList = zip(*ShuffleList)

    # Encode the labels
    Encoder = LabelEncoder()
    TrainingLabelList = Encoder.fit_transform(TrainingLabelList)
    print("[INFO] Found {} classes...".format(len(Encoder.classes_)))

    # Split the training data into training and test data
    (TrainPathList, TestPathList, TrainLabel, TestLabel) = train_test_split(TrainingImageList, TrainingLabelList, stratify = TrainingLabelList)

    Datasets = [("Train", TrainPathList, TrainLabel, Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + Network.TRAIN_HDF5 + ".hdf5"), 
                ("Test", TestPathList, TestLabel, Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + Network.TEST_HDF5 + ".hdf5")]

    # Create a new preprocessor to resize the images to a common size
    AspectPreprocessor = Preprocessor.AspectAwarePreprocessor(Width = Network.IMAGESIZE[0], Height = Network.IMAGESIZE[1])

    for (Type, PathList, Labels, FilePath) in Datasets:
        print("[INFO] Building dataset '{}'...".format(Type))
        FileWriter = IO.HDF5DatasetWriter(Dimensions = (len(PathList), Network.IMAGESIZE[0], Network.IMAGESIZE[1], Network.CHANNELS), 
                                            OutputPath = FilePath
        )

        # Create a new progressbar
        Widgets = ["    Progress: ", progressbar.Percentage(), " ", progressbar.Bar(), " ", progressbar.ETA()]
        Bar = progressbar.ProgressBar(maxval = len(PathList), widgets = Widgets).start()

        # Read out each image and write it into the hdf5 file
        for (ImageCounter, (Path, Label)) in enumerate(zip(PathList, Labels)):
            Image = cv2.imread(Path)
            Image = AspectPreprocessor.Preprocess(Image)

            if(Type == "Train"):
                # Get the mean values
                (b, g, r) = cv2.mean(Image)[:3]
                R.append(r)
                G.append(g)
                B.append(b)

            # Add the current image with the label to the hdf5 file
            FileWriter.AddData(Data = [Image], Label = [Label])

            # Update progressbar
            Bar.update(ImageCounter)

        # Store the labels in the training hdf5 file
        if(Type == "Train"):
            FileWriter.AddClasses(ClassLabels = Encoder.classes_)

        # Close the file
        FileWriter.Close()

        # Close the progressbar
        Bar.finish()

    # Store mean values
    print("[INFO] Serializing means...")
    with open(Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + Network.DATASET_MEAN, "w") as File:
        File.write(json.dumps({"R": numpy.mean(R), "G": numpy.mean(G), "B": numpy.mean(B)}))

if(args["train"] == True):
    print("[INFO] Step 2: Train network...")

    # Get the class labels from the training data
    print("[INFO] Read class labels...")
    with h5py.File(Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + Network.TRAIN_HDF5 + ".hdf5", "a") as File:
        ClassLabels = File["ClassLabels"].value
    print("[INFO] Found {} classes...".format(len(ClassLabels)))

    # Read the mean values
    Means = json.loads(open(Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + Network.DATASET_MEAN).read())

    # Create a new image generator
    ImageGenerator = ImageDataGenerator(rotation_range = 20, 
                                        zoom_range = 0.15, 
                                        width_shift_range = 0.2, 
                                        height_shift_range = 0.2, 
                                        shear_range = 0.15, 
                                        horizontal_flip = True, 
                                        fill_mode = "nearest"
    )

    # Create some preprocessors
    PP = Preprocessor.PatchPreprocessor(Width = Network.INPUT_SIZE[0], Height = Network.INPUT_SIZE[1])
    MP = Preprocessor.MeanPreprocessor(rMean = Means["R"], gMean = Means["G"], bMean = Means["B"])
    RP = Preprocessor.ResizePreprocessor(Width = Network.INPUT_SIZE[0], Height = Network.INPUT_SIZE[1])

    # Check if the database exist
    if(not(os.path.exists(Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + Network.TRAIN_HDF5 + ".hdf5"))):
        print("[ERROR] Can not find database! Abort...")
        exit()

    TrainGenerator = IO.HDF5DatasetGenerator(DB = Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + Network.TRAIN_HDF5 + ".hdf5", 
                                            BatchSize = Network.BATCH_SIZE, 
                                            Aug = ImageGenerator, 
                                            Preprocessors = [RP, PP, MP], 
                                            Classes = Network.CLASSES
    )
    TestGenerator = IO.HDF5DatasetGenerator(DB = Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + Network.TEST_HDF5 + ".hdf5", 
                                            BatchSize = Network.BATCH_SIZE, 
                                            Preprocessors = [RP, PP, MP], 
                                            Classes = Network.CLASSES
    )

    # Create a new model or load an existing checkpoint
    if(args["checkpoint"]):
        if(os.path.exists(args["checkpoint"])):
            print("[INFO] Load checkpoint {}...".format(args["checkpoint"]))
            Model = load_model(args["checkpoint"])
    
            # Update the learning rate
            print("[INFO] Old learning rate: {}".format(Backend.get_value(Model.optimizer.lr)))
            Backend.set_value(Model.optimizer.lr, Network.LEARNRATE)
            print("[INFO] New learning rate: {}".format(Backend.get_value(Model.optimizer.lr)))
        else:
            print("[ERROR] Model path does not exist! Abort...")
            exit()
    else:
        print("[INFO] Compiling model...")
        Model = NN.Build(Width = Network.INPUT_SIZE[0], 
                            Height = Network.INPUT_SIZE[1], 
                            Depth = Network.CHANNELS, 
                            Classes = Network.CLASSES
        )
        Model.compile(loss = "categorical_crossentropy", optimizer = Adam(lr = Network.LEARNRATE), metrics = ["accuracy"])

    # Create the checkpoint directory if it does not exist
    if(not(os.path.exists(Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + "checkpoints"))):
        os.makedirs(Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + "checkpoints")

    # Train the network
    print("[INFO] Start training...")
    Callbacks = [
        TrainingMonitor(FigurePath = os.path.sep.join([Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH, "{}.png".format(os.getpid())]),
                        StartAt = args["start"]
                        ),
        EpochCheckpoint(OutputPath = Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + "checkpoints", 
                        Every = 5, 
                        StartAt = args["start"]
                        )
    ]

    History = Model.fit_generator(generator = TrainGenerator.Generator(),
                                    steps_per_epoch = TrainGenerator.GetImageCount() // Network.BATCH_SIZE,
                                    validation_data = TestGenerator.Generator(),
                                    validation_steps = TestGenerator.GetImageCount() // Network.BATCH_SIZE,
                                    epochs = Network.EPOCHS,
                                    max_queue_size = Network.BATCH_SIZE * 2,
                                    callbacks = Callbacks,
                                    verbose = 1
    )
    
    # Write the model to disk
    print("[INFO] Save model...")
    Model.save(filepath = Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + Network.MODEL_NAME + ".hdf5", overwrite = True)

    try:
        plot_model(model = Model, to_file = Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + Network.MODEL_NAME + ".png", show_shapes = True)
    except:
        print("[ERROR] Can not plot the model!")

    # Save the class labels with the model
    with h5py.File(Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + Network.MODEL_NAME + ".hdf5", "a") as File:
        File.create_dataset("ClassLabels", data = numpy.array(ClassLabels, dtype = "S"))

    # Close the file generator
    TrainGenerator.Close()
    TestGenerator.Close()

if(args["predict"] == True):
    print("[INFO] Step 3: Perform predictions...")

    # Check if the model path exist
    if(not(os.path.exists(Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + Network.MODEL_NAME + ".hdf5"))):
        print("[ERROR] Model path does not exist! Abort...")
        exit()

    # Load the model from disk
    print("[INFO] Read model...")
    Model = load_model(filepath = Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + Network.MODEL_NAME + ".hdf5")

    # Create a new resize processor for resizing the images
    RP = Preprocessor.ResizePreprocessor(Width = Network.INPUT_SIZE[0], Height = Network.INPUT_SIZE[1])

    # Load the class labels from the model
    with h5py.File(Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + Network.MODEL_NAME + ".hdf5", "a") as File:
        ClassLabels = File["ClassLabels"].value
        ClassLabels = [x.decode("ascii") for x in ClassLabels]

    # Load images from disk
    for ImageNumber in range(100):
        # Load an input image
        Image = cv2.imread(Network.DATASET_PATH + os.path.sep + "validation" + os.path.sep + "{}.jpg".format(ImageNumber))

        if(not(Image is None)):
            # Resize the input image
            Crops = RP.Preprocess(Image = Image)

            Prediction = Model.predict(numpy.expand_dims(Crops, axis = 0))
            ClassIndex = Prediction.argmax(axis = 1)[0]

            print("[INFO] Read image {}...".format(Network.DATASET_PATH + os.path.sep + "validation" + os.path.sep + "{}.jpg".format(ImageNumber)))
            print("[INFO] Prediction: {} - Accuracy: {:.2f}%".format(ClassLabels[ClassIndex], Prediction[0][ClassIndex] * 100.0))

            # Display the input image	
            cv2.putText(Image, ClassLabels[ClassIndex], (10, 20), cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 0, 255), 2)
            cv2.imshow("Input", Image)
            cv2.waitKey(0)

if(args["convert"] == True):
    print("[INFO] Convert model...")

    # Convert the model to a tensorflow graph
    IO.Model2Graph.Convert(ModelPath = Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + Network.MODEL_NAME + ".hdf5", 
                            OutputPath = Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH
                            )

    # Get the class labels from the model
    with h5py.File(Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + Network.MODEL_NAME + ".hdf5", "r") as File:
        ClassLabels = File["ClassLabels"].value

    # Copy the class labels into a txt file
    if(ClassLabels is not None):
        with open(Network.DATASET_PATH + os.path.sep + Network.OUTPUT_PATH + os.path.sep + "Label.txt", "w") as File:
            for Label in ClassLabels:
                File.write(Label.decode("ascii") + os.linesep)