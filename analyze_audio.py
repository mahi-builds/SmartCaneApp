from pydub import AudioSegment
from pydub.silence import split_on_silence
import sys

try:
    audio = AudioSegment.from_file(r"c:\Users\Manasvi Bhargava\SmartCaneApp\video_assets\aura.mpeg")
    print(f"Total audio duration: {len(audio)/1000.0}s")
    
    # We look for silences of at least 1500ms (1.5 seconds)
    chunks = split_on_silence(audio, 
        min_silence_len=1500,
        silence_thresh=audio.dBFS-16,
        keep_silence=500
    )
    print(f"Found {len(chunks)} major segments separated by >1.5s silence.")
    
    for i, chunk in enumerate(chunks):
        print(f"Segment {i+1}: {len(chunk)/1000.0}s")
except Exception as e:
    print(e)
