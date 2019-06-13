# Subdirectory for data (relative path)
DATASET_PATH = "data"

# Subdirectories for training and validation (relative from DATASET_PATH)
VALIDATION = "validation"
TRAINING = "training"

# Output path (relative from DATASET_PATH)
OUTPUT_PATH = "output"

# Input image preprocessing settings
CHANNELS = 3
IMAGESIZE = (256, 256)

# Network and training settings
CLASSES = 2
BATCH_SIZE = 32
EPOCHS = 1
INPUT_SIZE = (100, 100)
LEARNRATE = 1e-6

# Output file names
MODEL_NAME = "Model"
TRAIN_HDF5 = "Train"
TEST_HDF5 = "Test"
DATASET_MEAN = "CatsVsDogs_mean.json"