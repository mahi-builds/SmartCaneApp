from moviepy import AudioFileClip
import numpy as np
import os

path = r"c:\Users\Manasvi Bhargava\SmartCaneApp\video_assets\aura.mpeg"
if not os.path.exists(path):
    print("File not found")
    exit()

try:
    audio = AudioFileClip(path)
    print(f"Loaded audio: {audio.duration}s, FPS: {audio.fps}")
    
    # We only process the first 120 seconds or so
    arr = audio.to_soundarray()
    if len(arr.shape) > 1:
        arr = arr.mean(axis=1)
        
    amp = np.abs(arr)
    # Smooth amplitude over 100ms
    fps = int(audio.fps)
    window = int(fps * 0.1)
    
    # We pad the array to avoid shape issues
    pad_len = window - (len(amp) % window)
    if pad_len != window:
        amp = np.pad(amp, (0, pad_len))
        
    smoothed = amp.reshape(-1, window).mean(axis=1)
    
    threshold = 0.015 # Very low volume threshold
    is_loud = smoothed > threshold
    
    # Find continuous blocks of loud vs quiet
    # each index in is_loud is 0.1 seconds
    segments = []
    current_state = False
    start_idx = 0
    
    for i, state in enumerate(is_loud):
        if state != current_state:
            duration_s = (i - start_idx) * 0.1
            if current_state: # Was loud
                if duration_s > 0.5: # Ignore blips
                    segments.append(("SPEECH", start_idx*0.1, i*0.1))
            else: # Was quiet
                if duration_s > 1.0: # Only care about big pauses
                    segments.append(("PAUSE", start_idx*0.1, i*0.1))
            start_idx = i
            current_state = state
            
    print("Detected Segments:")
    for s in segments:
        print(f"{s[0]}: {s[1]:.2f}s to {s[2]:.2f}s (Duration: {s[2]-s[1]:.2f}s)")

except Exception as e:
    print(f"Error: {e}")
