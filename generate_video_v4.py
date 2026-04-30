import os
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

from moviepy import VideoFileClip, ImageClip, concatenate_videoclips, ColorClip, VideoClip, CompositeVideoClip, AudioFileClip, CompositeAudioClip

ASSETS_DIR = r"c:\Users\Manasvi Bhargava\SmartCaneApp\video_assets"
OUTPUT_FILE = r"c:\Users\Manasvi Bhargava\SmartCaneApp\video_assets\draft4_professional.mp4"

def get_path(filename):
    return os.path.join(ASSETS_DIR, filename)

BGM_CLIP = None
try:
    BGM_CLIP = AudioFileClip(get_path("paulyudin-presentation-music-164836.mp3"))
except Exception as e:
    print("Warning: Could not load BGM", e)

global_time = 0.0

def get_bgm(start_time, duration, volume):
    if BGM_CLIP is None: return None
    track_len = BGM_CLIP.duration
    start = start_time % track_len
    end = start + duration
    
    if end <= track_len:
        segment = BGM_CLIP.subclipped(start, end)
    else:
        from moviepy import concatenate_audioclips
        part1 = BGM_CLIP.subclipped(start, track_len)
        part2 = BGM_CLIP.subclipped(0, end - track_len)
        segment = concatenate_audioclips([part1, part2])
        
    try:
        from moviepy.audio.fx import MultiplyVolume
        segment = segment.with_effects([MultiplyVolume(volume)])
    except:
        segment = segment.with_volume_multiplied(volume)
    return segment

def add_bgm_to_clip(clip, is_video=False):
    global global_time
    duration = clip.duration
    # Dynamic Ducking: 40% volume during title cards, 8% during video demos
    vol = 0.08 if is_video else 0.4
    
    bgm_segment = get_bgm(global_time, duration, vol)
    global_time += duration
    
    if bgm_segment is None: return clip
        
    if getattr(clip, 'audio', None) is not None and is_video:
        new_audio = CompositeAudioClip([clip.audio, bgm_segment])
        return clip.with_audio(new_audio)
    else:
        return clip.with_audio(bgm_segment)

def create_title_card(title, subtitle, duration=5.0):
    img = Image.new('RGBA', (1920, 1080), (15, 23, 42, 255))
    draw = ImageDraw.Draw(img)
    try:
        font_title = ImageFont.truetype("arialbd.ttf", 85)
        font_sub = ImageFont.truetype("arial.ttf", 50)
    except:
        font_title = ImageFont.load_default()
        font_sub = ImageFont.load_default()
        
    if hasattr(draw, 'textbbox'):
        bbox1 = draw.textbbox((0, 0), title, font=font_title)
        w1, h1 = bbox1[2] - bbox1[0], bbox1[3] - bbox1[1]
        bbox2 = draw.textbbox((0, 0), subtitle, font=font_sub)
        w2, h2 = bbox2[2] - bbox2[0], bbox2[3] - bbox2[1]
    else:
        w1, h1 = draw.textsize(title, font=font_title)
        w2, h2 = draw.textsize(subtitle, font=font_sub)
        
    total_h = h1 + h2 + 40
    y1 = (1080 - total_h) / 2
    y2 = y1 + h1 + 40
    
    draw.line([(1920/2 - 80, y2 - 20), (1920/2 + 80, y2 - 20)], fill=(234, 179, 8, 255), width=5)
    draw.text(((1920 - w1)/2, y1), title, font=font_title, fill=(255, 255, 255, 255))
    draw.text(((1920 - w2)/2, y2), subtitle, font=font_sub, fill=(148, 163, 184, 255))
    
    clip = ImageClip(np.array(img)).with_duration(duration)
    return add_bgm_to_clip(clip, is_video=False)

def ken_burns_effect(img_path, duration=5.0):
    if not os.path.exists(img_path): return None
    img = Image.open(img_path).convert('RGB')
    orig_w, orig_h = img.size
    target_ratio = 1920 / 1080.0
    img_ratio = orig_w / orig_h
    if img_ratio > target_ratio:
        new_w = int(orig_h * target_ratio)
        img = img.crop(((orig_w - new_w)//2, 0, (orig_w + new_w)//2, orig_h))
    else:
        new_h = int(orig_w / target_ratio)
        img = img.crop((0, (orig_h - new_h)//2, orig_w, (orig_h + new_h)//2))
    base_arr = np.array(img.resize((1920, 1080), Image.Resampling.LANCZOS))
    
    def make_frame(t):
        z = 1.0 + 0.15 * (t / duration)
        cw, ch = int(1920 / z), int(1080 / z)
        x1, y1 = (1920 - cw)//2, (1080 - ch)//2
        cr = Image.fromarray(base_arr).crop((x1, y1, x1+cw, y1+ch))
        return np.array(cr.resize((1920, 1080), Image.Resampling.BILINEAR))
        
    clip = VideoClip(make_frame, duration=duration)
    return add_bgm_to_clip(clip, is_video=False)

def prepare_professional_clip(path, start=None, end=None, speed=1.0, blur_phone=False):
    if not os.path.exists(path): return None
    try:
        clip = VideoFileClip(path)
        if start is not None and end is not None: clip = clip.subclipped(start, end)
        elif start is not None: clip = clip.subclipped(start)
        elif end is not None: clip = clip.subclipped(0, end)
            
        if speed != 1.0:
            try: 
                from moviepy.video.fx import MultiplySpeed
                clip = clip.with_effects([MultiplySpeed(speed)])
            except: pass
            
        clip = clip.resized(height=1080)
        
        if blur_phone:
            def redact(image):
                frame = image.copy()
                h, w, _ = frame.shape
                # Redact top portion where number appears in SMS (e.g. 5% to 15% height)
                y1, y2 = int(h*0.04), int(h*0.14)
                frame[y1:y2, :] = 0
                return frame
            if hasattr(clip, 'image_transform'): clip = clip.image_transform(redact)
            else: clip = clip.fl_image(redact)
            
        # Professional Dark Slate Background
        bg = ColorClip(size=(1920, 1080), color=(15, 23, 42)).with_duration(clip.duration)
        comp = CompositeVideoClip([bg, clip.with_position("center")])
        comp.audio = clip.audio
        return add_bgm_to_clip(comp, is_video=True)
    except Exception as e:
        print(f"Error processing {path}: {e}")
        return None

def main():
    clips = []
    clips.append(create_title_card("Introducing AURA", "An intelligent, low-cost Edge-AI mobility system."))
    clips.append(ken_burns_effect(get_path("1scene2.jpeg"), 5.0))
    
    clips.append(create_title_card("Smart Hardware Integration", "ESP32 powered with Ultrasonic, IR, and Water sensors\nfor obstacle, drop-off, and wet surface detection."))
    clips.append(prepare_professional_clip(get_path("scene3.mp4"), speed=1.5))
    
    clips.append(create_title_card("Intelligent Android Application", "Delivering continuous real-time Text-to-Speech (TTS) guidance."))
    clips.append(prepare_professional_clip(get_path("scene4.mp4")))
    
    clips.append(create_title_card("Real-time Obstacle Detection", "Instant TTS alerts for immediate environmental awareness."))
    clips.append(prepare_professional_clip(get_path("scene5A1.mp4"), start=0, end=6))
    clips.append(prepare_professional_clip(get_path("scene5A3.mp4"), start=3.6, end=13.6))
    
    clips.append(create_title_card("Edge-AI Hazard Recognition", "Detects descending stairs, doors, and uneven surfaces."))
    clips.append(prepare_professional_clip(get_path("scene5B2.mp4"), start=2.2, end=6.2))
    clips.append(prepare_professional_clip(get_path("scene5B1.mp4"), start=5, end=8))
    
    clips.append(create_title_card("OCR Text Reader", "Multilingual text scanning and voice-command navigation."))
    clips.append(prepare_professional_clip(get_path("scene5C.mp4"), start=3, end=14))
    clips.append(prepare_professional_clip(get_path("scene5C.mp4"), start=27, end=41))
    
    clips.append(create_title_card("Emergency Support System", "Instantly sends live GPS locations and SMS alerts to registered contacts."))
    clips.append(prepare_professional_clip(get_path("scene5D1.mp4"), start=3.1, blur_phone=True))
    
    # End scenes
    img_clip = ImageClip(get_path("scene5D2.jpeg")).resized(height=1080).with_duration(3.0)
    bg = ColorClip(size=(1920, 1080), color=(15, 23, 42)).with_duration(3.0)
    img_comp = CompositeVideoClip([bg, img_clip.with_position("center")])
    clips.append(add_bgm_to_clip(img_comp, is_video=False))
    
    clips.append(create_title_card("Low-cost. Portable. Offline.", "Built for everyone, everywhere."))
    clips.append(ken_burns_effect(get_path("scene6.jpeg"), 5.0))
    clips.append(create_title_card("AURA", "Empowering independence through technology."))

    final_clip = concatenate_videoclips(clips, method="compose")
    final_clip.write_videofile(OUTPUT_FILE, fps=24, codec="libx264", audio_codec="aac", audio_fps=44100)
    print("Draft 4 Done!")

if __name__ == "__main__":
    main()
