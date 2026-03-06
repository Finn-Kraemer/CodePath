import re
import sys
import os

def split_content(content):
    # Find where the Aufgabe starts.
    # It usually starts with <p><strong>Aufgabe:</strong> or <strong>Aufgabe:</strong>
    match = re.search(r'(<p>)?<strong>Aufgabe:</strong>', content)
    if match:
        story_part = content[:match.start()].strip()
        task_part = content[match.start():].strip()
        
        # Clean story_part
        story_part = re.sub(r'^<p><strong>Story:</strong>\s*', '<p>', story_part)
        story_part = re.sub(r'<strong>Story:</strong>\s*', '', story_part)
        
        # Clean task_part
        task_part = re.sub(r'^<p><strong>Aufgabe:</strong>\s*', '<p>', task_part)
        task_part = re.sub(r'<strong>Aufgabe:</strong>\s*', '', task_part)
        
        # If task_part is just <p> or similar, it might be an issue, but let's assume it has content
    else:
        # Only story
        story_part = content.strip()
        story_part = re.sub(r'^<p><strong>Story:</strong>\s*', '<p>', story_part)
        story_part = re.sub(r'<strong>Story:</strong>\s*', '', story_part)
        task_part = ""

    # Final check for empty paragraphs or just tags
    if story_part == "<p></p>" or story_part == "<p>": story_part = ""
    if task_part == "<p></p>" or task_part == "<p>": task_part = ""
    
    return story_part, task_part

def process_file(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    def replacement(match):
        prefix = match.group(1) # createTask(m, "slug", "title",
        desc_str = match.group(2) # description
        suffix = match.group(3) # ", TaskType.
        
        story, task = split_content(desc_str)
        
        return f'{prefix}"{story}", "{task}"{suffix}'

    # Pattern for createTask(m, "slug", "title", "description", TaskType...
    pattern = r'(createTask\s*\(\s*m\s*,\s*".*?"\s*,\s*".*?"\s*,\s*")' 
    pattern += r'((?:[^"\\]|\\.)*?)' 
    pattern += r'("\s*,\s*TaskType\.)' 
    
    new_content = re.sub(pattern, replacement, content, flags=re.DOTALL)
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(new_content)

if __name__ == "__main__":
    for arg in sys.argv[1:]:
        if os.path.isfile(arg):
            print(f"Processing {arg}...")
            process_file(arg)
        else:
            print(f"Skipping {arg} (not a file)")
