#!/usr/bin/env ruby

require 'xcodeproj'
require 'xcodeproj/project/object/target_dependency'

project_path = "#{Dir.pwd}/#{Dir['*.xcodeproj'][0]}"
project = Xcodeproj::Project.open(project_path)
project.native_targets.each do |target|
	target.dependencies.each do |dep|
		if (dep.name != nil)
			changed = false
			sub_project = dep.target_proxy.proxied_object.project
			sub_project.native_targets.each do |sub_target|
				sub_target.build_configurations.each do |config|
					old_fsp = config.build_settings['FRAMEWORK_SEARCH_PATHS']
					if (!(old_fsp.include? "$(CONFIGURATION_BUILD_DIR)/**"))
						changed = true
						config.build_settings['FRAMEWORK_SEARCH_PATHS'] << "$(CONFIGURATION_BUILD_DIR)/**"
						puts "Small: Add framework search paths for '#{dep.name}'"
					end
				end
			end

			if (changed)
				sub_project.save
			end
		end
	end
end
