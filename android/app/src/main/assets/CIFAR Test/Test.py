import cv2
from keras.models import load_model
from keras.preprocessing.image import img_to_array
import numpy

labelNames = ["airplane", "automobile", "bird", "cat", "deer",
	"dog", "frog", "horse", "ship", "truck"]

Image = cv2.imread("16_dog.png")
Image = Image.astype("float") / 255.0

ArrayImage = img_to_array(Image)
ArrayImage = numpy.expand_dims(ArrayImage, axis = 0)

model = load_model("CIFAR10.hdf5")
model.summary()
H = model.predict([ArrayImage])
print(labelNames[H.argmax(axis=1)[0]])
