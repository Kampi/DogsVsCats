@echo off

if exist tensorflow_log_dir rd /s /q tensorflow_log_dir
python .\import_pb_to_tensorboard.py --model_dir data/output/Model.pb --log_dir=tensorflow_log_dir
tensorboard --logdir=tensorflow_log_dir