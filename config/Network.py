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
EPOCHS = 5
INPUT_SIZE = (227, 227)
LEARNRATE = 1e-5

# Output file names
MODEL_NAME = "Model"
TRAIN_HDF5 = "Train"
TEST_HDF5 = "Test"
DATASET_MEAN = "CatsVsDogs_mean.json"