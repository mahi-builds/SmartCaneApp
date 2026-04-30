import os
import sys

# Try importing moviepy, if fails print error
try:
    from moviepy import VideoFileClip, ImageClip, concatenate_videoclips, ColorClip
except ImportError as e:
    print(f"Error importing moviepy: {e}")
    sys.exit(1)

ASSETS_DIR = r"c:\Users\Manasvi Bhargava\SmartCaneApp\video_assets"
OUTPUT_FILE = r"c:\Users\Manasvi Bhargava\SmartCaneApp\video_assets\draft1.mp4"

def get_path(filename):
    return os.path.join(ASSETS_DIR, filename)

def prepare_clip(path, duration=None, is_image=False):
    if not os.path.exists(path):
        print(f"Missing: {path}")
        return None
    try:
        if is_image:
            clip = ImageClip(path).with_duration(duration if duration else 4)
        else:
            clip = VideoFileClip(path)
            if duration:
                clip = clip.with_duration(duration)
        
        # Resize to 1080p height to make sure they are somewhat uniform
        return clip.resized(height=1080)
    except Exception as e:
        print(f"Error processing {path}: {e}")
        return None

def main():
    clips = []
    
    # 1. Cold Open (Black screen for 2 secs)
    black_clip = ColorClip(size=(1920, 1080), color=(0, 0, 0), duration=2)
    clips.append(black_clip)

    # 2. Scene 2 (Image)
    c = prepare_clip(get_path("1scene2.jpeg"), duration=4, is_image=True)
    if c: clips.append(c)
    
    # 3. Scene 3
    c = prepare_clip(get_path("scene3.mp4"))
    if c: clips.append(c)

    # 4. Scene 4
    c = prepare_clip(get_path("scene4.mp4"))
    if c: clips.append(c)

    # 5. Scene 5A
    for f in ["scene5A1.mp4", "scene5A2.mp4", "scene5A3.mp4"]:
        c = prepare_clip(get_path(f))
        if c: clips.append(c)

    # 6. Scene 5B
    for f in ["scene5B1.mp4", "scene5B2.mp4"]:
        c = prepare_clip(get_path(f))
        if c: clips.append(c)
        
    # 7. Scene 5C
    c = prepare_clip(get_path("scene5C.mp4"))
    if c: clips.append(c)
        
    # 8. Scene 5D
    c = prepare_clip(get_path("scene5D1.mp4"))
    if c: clips.append(c)
    c = prepare_clip(get_path("scene5D2.jpeg"), duration=3, is_image=True)
    if c: clips.append(c)
        
    # 9. Scene 6
    c = prepare_clip(get_path("scene6.jpeg"), duration=5, is_image=True)
    if c: clips.append(c)

    if not clips:
        print("No clips found to concatenate.")
        return
    
    print(f"Found {len(clips)} clips. Concatenating...")
    final_clip = concatenate_videoclips(clips, method="compose")
    
    print(f"Writing video to {OUTPUT_FILE}...")
    final_clip.write_videofile(OUTPUT_FILE, fps=24, codec="libx264", audio_codec="aac")
    print("Done!")

if __name__ == "__main__":
    main()
